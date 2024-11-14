package io.github.irevive.union.derivation

import scala.quoted.*

object UnionDerivation {

  inline def derive[F[_], A]: F[A] = ${ deriveImpl[F, A] }

  def deriveImpl[F[_], A](using Quotes, Type[F], Type[A]): Expr[F[A]] = {
    val m = new Macro
    m.deriveImpl
  }

  private class Macro[F[_]: Type](using quotes: Quotes) {
    import quotes.reflect.*

    def deriveImpl[A: Type]: Expr[F[A]] = {
      given Diagnostic = Diagnostic(TypeRepr.of[F], TypeRepr.of[A].dealias)

      val tpe: TypeRepr = TypeRepr.of[A]

      tpe.dealias match {
        case o: OrType =>
          val paramType      = findParamType
          val abstractMethod = findAbstractMethod
          val collectedTypes = collectTypes(o)
          val params         = collectParams(abstractMethod, paramType.tpe)
          val resultType     = detectResultType[A](abstractMethod)

          val lambdaType = MethodType(params.map(_.name))(
            _ => params.map(p => if (p.isPoly) tpe else p.typeRepr),
            _ => resultType
          )

          val lambda = Lambda(
            Symbol.spliceOwner,
            lambdaType,
            (_, args) => body[A](collectedTypes, params, args, abstractMethod.name)
          )

          // transform lambda to an instance of the typeclass
          val instanceTree = lambda match {
            case Block(body, Closure(method, _)) =>
              Block(body, Closure(method, Some(TypeRepr.of[F].appliedTo(tpe))))
          }

          instanceTree.asExprOf[F[A]]

        case other =>
          errorAndAbort("only Union type is supported.")
      }
    }

    private final case class Diagnostic(typeclass: TypeRepr, targetType: TypeRepr)

    private final case class MethodParam(
        name: String,
        typeRepr: TypeRepr,
        isPoly: Boolean // whether param appear in the polymorphic position, e.g. (a: A)
    )

    private def collectParams(method: DefDef, paramType: TypeRepr)(using Diagnostic): List[MethodParam] =
      method.paramss match {
        case TermParamClause(params) :: Nil =>
          val all = params.map { param =>
            MethodParam(param.name, param.tpt.tpe, param.tpt.tpe == paramType)
          }

          val typed = all.filter(_.isPoly)

          if (typed.size == 1) {
            all
          } else if (typed.isEmpty) {
            errorAndAbort(
              "the abstract method without the polymorphic param isn't supported.",
              Some(
                """check the example below where the instance cannot be derived
                  |
                  |trait Typeclass[A] {
                  |  def magic(a: Int): String
                  |  //              ^
                  |  // Polymorphic param of type A is missing
                  |}""".stripMargin
              )
            )
          } else {
            errorAndAbort(
              s"the abstract method has multiple polymorphic params of the same parametrized type: ${typed.map(_.name).mkString(", ")}.",
              Some("""check the example below where the instance cannot be derived
                  |
                  |trait Typeclass[A] {
                  |  def magic(a1: A, b: Int, a2: A): String
                  |  //            ^              ^
                  |  // Polymorphic type A appears in two positions
                  |}""".stripMargin)
            )
          }

        case Nil =>
          errorAndAbort(
            "the abstract method without the polymorphic param isn't supported.",
            Some(
              """check the example below where the instance cannot be derived
                |
                |trait Typeclass[A] {
                |  def magic: String
                |  //       ^
                |  // Polymorphic param of type A is missing
                |}""".stripMargin
            )
          )

        case _ =>
          errorAndAbort(
            "the curried abstract method isn't supported.",
            Some(
              """check the example below where the instance cannot be derived
                |
                |trait Typeclass[A] {
                |  def magic(a: A)(b: Int): String
                |  //             ^
                |  // Curried functions aren't supported
                |}""".stripMargin
            )
          )
      }

    /**
      * Detects concrete result type of the abstract method.
      */
    private def detectResultType[A: Type](method: DefDef)(using Diagnostic): TypeRepr =
      TypeRepr.of[F[A]].memberType(method.symbol) match {
        case mt: MethodType => mt.resType
        case _              => errorAndAbort(s"cannot detect result type of the '${method.name}' function.")
      }

    /**
      * Exactly one type param is required.
      */
    private def findParamType(using Diagnostic): TypeTree =
      TypeRepr.of[F].typeSymbol.declaredTypes match {
        case head :: Nil =>
          TypeIdent(head)

        case Nil =>
          errorAndAbort("The typeclass doesn't have a type parameter")

        case _ =>
          errorAndAbort("The typeclass has multiple type parameters")
      }

    /**
      * Looks-up for an abstract method in F[_]
      */
    private def findAbstractMethod(using Diagnostic): DefDef = {
      val tcl: TypeRepr = TypeRepr.of[F]

      val methods = tcl.typeSymbol.declaredMethods.filter(_.isDefDef).map(_.tree).collect {
        case dd @ DefDef(name, paramss, tpt, None) => dd
      }

      methods match {
        case Nil =>
          errorAndAbort(
            "cannot detect an abstract method in the typeclass.",
            Some("""`scalacOptions += "-Yretain-trees"` may solve the issue.""")
          )

        case head :: Nil =>
          head

        case other =>
          errorAndAbort(
            s"more than one abstract method is detected: ${other.map(_.name).mkString(", ")}."
          )
      }
    }

    /**
      * Generates the body of a typeclass. Creates a set of if-else statements using known types of the union.
      *
      * The
      * {{{
      *   if (value.isInstanceOf[Int]) summon[Typeclass[Int]].magic(value)
      *   else if (value.isInstanceOf[String]) summon[Typeclass[String]].magic(value)
      *   else sys.error("Impossible") // impossible state
      * }}}
      *
      * @param knownTypes
      *   the known member types of the union
      * @param params
      *   the list of function parameter
      * @param lambdaArgs
      *   the list of lambda args
      * @param method
      *   the name of the typeclass method to apply
      * @tparam A
      *   the input union type
      */
    private def body[A: Type](
        knownTypes: List[TypeRepr],
        params: List[MethodParam],
        lambdaArgs: List[Tree],
        method: String
    )(using Diagnostic): Term = {

      val selector: Term = params
        .zip(lambdaArgs)
        .collectFirst { case (param, arg) if param.isPoly => arg }
        .getOrElse(errorAndAbort("cannot find poly param in the list of lambda arguments."))
        .asExprOf[A]
        .asTerm

      val ifBranches: List[(Term, Term)] = knownTypes.map { tpe =>
        val identifier = TypeIdent(tpe.typeSymbol)
        val condition  = TypeApply(Select.unique(selector, "isInstanceOf"), identifier :: Nil)
        val tcl        = lookupImplicit(tpe)

        val args: List[Term] = params.zip(lambdaArgs).map {
          case (param, arg) if param.isPoly =>
            Select.unique(selector, "asInstanceOf").appliedToType(tpe)

          case (_, arg) =>
            arg.asExpr.asTerm
        }

        val action: Term = Select.unique(tcl, method).appliedToArgs(args)

        (condition, action)
      }

      mkIfStatement(ifBranches)
    }

    /**
      * Collect all known types of the Union.
      *
      * Example: Int | (String | Double) | Long. Returns: List(Int, String, Double, Long)
      */
    private def collectTypes(start: OrType): List[TypeRepr] = {
      def loop(tpe: TypeRepr, output: List[TypeRepr]): List[TypeRepr] =
        tpe.dealias match {
          case o: OrType => loop(o.left, output) ::: loop(o.right, output) ::: output
          case r         => r +: output
        }

      loop(start, Nil)
    }

    /**
      * Looks-up for an instance of `F[A]` for the provided type
      */
    private def lookupImplicit(t: TypeRepr)(using Diagnostic): Term = {
      val typeclassTpe = TypeRepr.of[F]
      val tclTpe       = typeclassTpe.appliedTo(t)
      Implicits.search(tclTpe) match {
        case success: ImplicitSearchSuccess => success.tree
        case failure: ImplicitSearchFailure => errorAndAbort(failure.explanation)
      }
    }

    /**
      * Creates the if-else statement from the supplied branches
      */
    private def mkIfStatement(branches: List[(Term, Term)]): Term =
      branches match {
        case (p1, a1) :: xs =>
          If(p1, a1, mkIfStatement(xs))
        case Nil =>
          ('{ throw RuntimeException("Unhandled condition encountered during derivation") }).asTerm
      }

    private def errorAndAbort(reason: String, hint: Option[String] = None)(using d: Diagnostic): Nothing =
      report.errorAndAbort(
        s"""UnionDerivation cannot derive an instance of ${d.typeclass.typeSymbol} for the type `${d.targetType.show}`.
           |Reason: $reason""".stripMargin + hint.map(fix => s"\nHint: $fix").getOrElse("") + "\n\n"
      )
  }
}
