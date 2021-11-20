package io.gitlab.mhammons.slinc

import jdk.incubator.foreign.{MemoryLayout, CLinker}
import scala.util.chaining.*
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Map
import java.lang.invoke.VarHandle
import java.lang.invoke.MethodHandle
import io.gitlab.mhammons.slinc.components.MemLayout
import io.gitlab.mhammons.slinc.components.NamedVarhandle
import io.gitlab.mhammons.slinc.components.StructLayout

//todo: make NativeCache just a trait...

trait NativeCache:
   def getLayout(name: String, layout: => StructLayout): StructLayout
   def getVarHandles(
       name: String,
       varHandles: => Seq[NamedVarhandle]
   ): Seq[NamedVarhandle]
   def getDowncall(name: String, mh: => MethodHandle): MethodHandle
   inline def layout[A]: StructLayout =
      getLayout(LayoutMacros.layoutName[A], LayoutMacros.deriveLayout2[A])
   inline def varHandles[A]: Seq[NamedVarhandle] =
      getVarHandles(
        LayoutMacros.layoutName[A],
        StructMacros.genVarHandles[A]
      )

   inline def sortedVarHandles[A]: Seq[VarHandle] =
      getVarHandles(
        LayoutMacros.layoutName[A],
        StructMacros.genVarHandles[A]
      ).sortBy(_.name).map(_.varhandle)
   def downcall(name: String, mh: => MethodHandle): MethodHandle =
      getDowncall(name, mh)
   val clinker: CLinker

class NativeCacheDefaultImpl extends NativeCache:
   private[slinc] val layouts = TrieMap.empty[String, StructLayout]
   private[slinc] val varHandlesMap =
      TrieMap.empty[String, Seq[NamedVarhandle]]
   private[slinc] val methodHandles = TrieMap.empty[String, MethodHandle]

   def getDowncall(name: String, mh: => MethodHandle) =
      methodHandles.getOrElseUpdate(name, mh)
   def getLayout(name: String, layout: => StructLayout) =
      layouts.getOrElseUpdate(name, layout)

   def getVarHandles(
       name: String,
       varHandles: => Seq[NamedVarhandle]
   ) =
      varHandlesMap.getOrElseUpdate(name, varHandles)

   val clinker = CLinker.getInstance

object NativeCache:
   given NativeCache = NativeCacheDefaultImpl()
