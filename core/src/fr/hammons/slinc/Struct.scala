package fr.hammons.slinc

import scala.deriving.Mirror
import scala.compiletime.{erasedValue, summonInline, constValueTuple}
import scala.reflect.ClassTag
import modules.DescriptorModule
import fr.hammons.slinc.modules.TransitionModule
import fr.hammons.slinc.modules.ReadWriteModule
import fr.hammons.slinc.modules.Reader
import fr.hammons.slinc.modules.Writer
import scala.annotation.nowarn

trait Struct[A <: Product] extends DescriptorOf[A]

object Struct:
  private inline def memberDescriptors[A](using
      m: Mirror.ProductOf[A]
  ): List[TypeDescriptor] =
    memberDescriptorsHelper[m.MirroredElemTypes]
  private inline def memberDescriptorsHelper[T <: Tuple]: List[TypeDescriptor] =
    inline erasedValue[T] match
      case _: (h *: t) =>
        summonInline[DescriptorOf[h]].descriptor :: memberDescriptorsHelper[t]
      case _: EmptyTuple => Nil

  /** Summons up the names of members of the Product A
    *
    * @tparam A
    *   A product type representing a C struct.
    * @return
    */
  private inline def memberNames[A](using m: Mirror.ProductOf[A]) =
    constValueTuple[m.MirroredElemLabels].toArray.map(_.toString())

  private inline def writeGen[A <: Product](using
      m: Mirror.ProductOf[A],
      rwm: ReadWriteModule,
      dm: DescriptorModule
  ): Writer[A] =
    val offsets = dm.memberOffsets(memberDescriptors[A])
    (mem, offset, value) =>
      writeGenHelper(
        offsets.map(_ + offset),
        0,
        Tuple.fromProductTyped(value),
        mem
      )

  private inline def writeGenHelper[A <: Tuple](
      offsets: IArray[Bytes],
      index: Int,
      value: A,
      mem: Mem
  )(using rwm: ReadWriteModule): Unit =
    inline value match
      case tuple: (h *: t) =>
        rwm.write(
          mem,
          offsets(index),
          summonInline[DescriptorOf[h]].descriptor,
          tuple.head
        )
        writeGenHelper(offsets, index + 1, tuple.tail, mem)
      case EmptyTuple => ()

  private inline def readGen[A <: Product](using
      m: Mirror.ProductOf[A],
      rwm: ReadWriteModule,
      dm: DescriptorModule
  ): Reader[A] =
    val offsets: IArray[Bytes] = dm.memberOffsets(memberDescriptors[A])
    (mem, offset) => {
      val elems: m.MirroredElemTypes =
        readGenHelper[m.MirroredElemTypes](offsets.map(_ + offset), 0, mem)
      m.fromTuple(elems)
    }

  private inline def readGenHelper[A <: Tuple](
      offsets: IArray[Bytes],
      index: Int,
      mem: Mem
  )(using rwm: ReadWriteModule): A =
    inline erasedValue[A] match
      case _: (h *: t) =>
        inline rwm.read(
          mem,
          offsets(index),
          summonInline[DescriptorOf[h]].descriptor
        ) *: readGenHelper[t](offsets, index + 1, mem) match
          case r: A => r
      case _: EmptyTuple =>
        inline EmptyTuple match
          case a: A => a

  inline def derived[A <: Product](using
      m: Mirror.ProductOf[A],
      ct: ClassTag[A]
  ) = new Struct[A]:
    type Inner = A
    val descriptor: StructDescriptor { type Inner = A } =
      new StructDescriptor(
        memberDescriptors[A].view
          .zip(memberNames[A])
          .map(StructMemberDescriptor.apply)
          .toList,
        ct.runtimeClass,
        m.fromProduct(_)
      ):
        type Inner = A
        val reader = readGen[A]
        val writer = writeGen[A]

        @nowarn("msg=unused implicit parameter")
        override val returnTransition = returnValue =>
          val mem = summon[TransitionModule].memReturn(returnValue)
          summon[ReadWriteModule].read(mem, Bytes(0), this)

        @nowarn("msg=unused implicit parameter")
        override val argumentTransition = argument =>
          val mem = summon[Allocator].allocate(this, 1)
          summon[ReadWriteModule].write(mem, Bytes(0), this, argument)
          summon[TransitionModule].methodArgument(mem).asInstanceOf[Object]
