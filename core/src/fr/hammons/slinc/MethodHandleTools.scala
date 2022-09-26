package fr.hammons.slinc

import scala.quoted.*
import java.lang.invoke.MethodHandle

object MethodHandleTools:
  def exprNameMapping(expr: Expr[Any])(using Quotes): String =
    if expr.isExprOf[Int] then "I"
    else if expr.isExprOf[Short] then "S"
    else if expr.isExprOf[Long] then "L"
    else if expr.isExprOf[Byte] then "B"
    else if expr.isExprOf[Double] then "D"
    else if expr.isExprOf[Float] then "F"
    else "O"

  def returnMapping[R](using Quotes, Type[R]) =
    Type.of[R] match
      case '[Int]    => "I"
      case '[Short]  => "S"
      case '[Long]   => "L"
      case '[Byte]   => "B"
      case '[Double] => "D"
      case '[Float]  => "F"
      case '[Object] => "O"

  def invokeArguments[R](mh: Expr[MethodHandle], exprs: Expr[Any]*)(using
      Quotes,
      Type[R]
  ) =
    import quotes.reflect.*

    // val mod =
    //   TypeRepr.of[MethodHandleFacade].classSymbol.getOrElse(???).companionModule

    val arity = exprs.size
    val callName = (exprs.map(exprNameMapping) :+ returnMapping[R]).mkString

    val mod = Symbol.requiredPackage("fr.hammons.slinc").declarations.find(_.name == s"MethodHandleArity$arity")

    val backupMod = TypeRepr
      .of[MethodHandleFacade]
      .classSymbol
      .getOrElse(report.errorAndAbort("This class should exist!!"))
      .companionModule

    val methodSymbol = mod.flatMap(_.declaredMethods
      .find(_.name == callName))

    methodSymbol.foreach(println)


    val backupSymbol =
      backupMod.declaredMethods.find(_.name.endsWith(arity.toString()))

    val call = methodSymbol
      .map(ms =>
        Apply(
          Select(Ident(mod.get.termRef), ms),
          mh.asTerm :: exprs.map(_.asTerm).toList
        ).asExpr
      )
      .orElse(
        backupSymbol.map(ms =>
          Apply(
            Select(Ident(backupMod.termRef), ms),
            mh.asTerm :: exprs.map(_.asTerm).toList
          ).asExpr
        )
      )
      .getOrElse(
        '{ MethodHandleFacade.callVariadic($mh, ${ Varargs(exprs) }*) }
      )

    val expr = call
    report.info(expr.show)
    expr

  def calculateMethodHandleImplementation[L](
      platformExpr: Expr[LibraryI.PlatformSpecific],
      addresses: Expr[IArray[Object]]
  )(using Quotes, Type[L]) =
    import quotes.reflect.*

    val methodSymbols = MacroHelpers.getMethodSymbols(
      TypeRepr
        .of[L]
        .classSymbol
        .getOrElse(
          report.errorAndAbort(
            s"Can't calculate methodhandles from type ${Type.show[L]}"
          )
        )
    )

    val exprs = methodSymbols
      .map(
        MacroHelpers.getInputsAndOutputType(_)
      )
      .zipWithIndex
      .map { case ((is, o), addressIdx) =>
        val inputs = Expr.ofSeq(is.map { case '{ ${ _ }: a } =>
          LayoutI.getLayoutFor[a]
        })
        val oLayout = o match
          case '[Unit] => '{ None }
          case '[o] =>
            val layout = LayoutI.getLayoutFor[o]
            '{ Some($layout) }

        '{
          $platformExpr
            .getDowncall($inputs, $oLayout)
            .bindTo($addresses(${ Expr(addressIdx) }))
            .nn
        }
      }

    '{ IArray(${ Varargs(exprs) }*) }

  inline def calculateMethodHandles[L](
      platformSpecific: LibraryI.PlatformSpecific,
      addresses: IArray[Object]
  ) = ${
    calculateMethodHandleImplementation[L]('platformSpecific, 'addresses)
  }
