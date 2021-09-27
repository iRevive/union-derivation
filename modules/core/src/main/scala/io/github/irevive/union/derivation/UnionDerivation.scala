package io.github.irevive.union.derivation

import scala.quoted.*

object UnionDerivation {

  inline def derive[F[_], A]: F[A] = ${ deriveImpl[F, A] }

  def deriveImpl[F[_], A](using Quotes, Type[F], Type[A]): Expr[F[A]] = {
    val m = new Macro
    m.deriveImpl
  }

  class Macro[F[_]: Type](using quotes: Quotes) {
    import quotes.reflect.*

    def deriveImpl[A: Type]: Expr[F[A]] = {
      val tpe: TypeRepr = TypeRepr.of[A]

      tpe.dealias match {
        case o: OrType =>
          val abstractMethod = findAbstractMethod
          val knownTypes     = collectTypes(o).map(_.typeSymbol)
          val mt             = MethodType(List("a"))(_ => List(tpe), _ => abstractMethod.returnTpt.tpe)

          val lambda =
            Lambda(Symbol.spliceOwner, mt, (meth, arg) => body(arg.head.asExprOf[A], knownTypes, abstractMethod.name))

          // transform lambda to an instance of the typeclass
          val instanceTree = lambda match {
            case Block(body, Closure(meth, _)) =>
              Block(body, Closure(meth, Some(TypeRepr.of[F].appliedTo(tpe))))
          }

          instanceTree.asExprOf[F[A]]

        case other =>
          report.throwError(s"Cannot derive a typeclass for the ${tpe.show}. Only Union type is supported")
      }
    }

    /**
      * Looks-up for an abstract method in F[_]
      */
    private def findAbstractMethod: DefDef = {
      val tcl: TypeRepr = TypeRepr.of[F]

      val methods = tcl.typeSymbol.declaredMethods.filter(_.isDefDef).map(_.tree).collect {
        case dd @ DefDef(name, paramss, tpt, None) => dd
      }

      methods match {
        case Nil =>
          report.throwError(
            s"""Cannot detect an abstract method in ${tcl.typeSymbol}. `scalacOptions += "-Yretain-trees"` may solve the issue"""
          )

        case head :: Nil =>
          head

        case other =>
          report.throwError(
            s"More than one abstract method detected in ${tcl.typeSymbol}: ${other.map(_.name).mkString(", ")}. Automatic derivation is impossible"
          )
      }
    }

    /**
      * Generates the body of a typeclass. Creates a set of if-else statements using known types of the union.
      *
      * The
      * {{{
      *   if (value.isInstanceOf[Int]) summon[Show[Int]].show(value)
      *   else if (value.isInstanceOf[String]) summon[Show[String]].show(value)
      *   else sys.error("Impossible") // impossible state
      * }}}
      *
      * @param t
      *   the input value of the method
      * @param knownTypes
      *   the known member types of the union
      * @param method
      *   the name of the typeclass method to apply
      * @tparam A
      *   the input type
      * @tparam R
      *   the output type of the method
      * @return
      */
    private def body[A](t: Expr[A], knownTypes: List[Symbol], method: String): Term = {
      val selector: Term = t.asTerm

      val ifBranches: List[(Term, Term)] = knownTypes.map { sym =>
        val childTpe    = TypeIdent(sym)
        val condition   = TypeApply(Select.unique(selector, "isInstanceOf"), childTpe :: Nil)
        val tcl         = lookupImplicit(childTpe.tpe)
        val castedValue = Select.unique(selector, "asInstanceOf").appliedToType(childTpe.tpe)

        val action: Term = Apply(Select.unique(tcl, method), castedValue :: Nil)

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
    private def lookupImplicit(t: TypeRepr): Term = {
      val typeclassTpe = TypeRepr.of[F]
      val tclTpe       = typeclassTpe.appliedTo(t)
      Implicits.search(tclTpe) match {
        case success: ImplicitSearchSuccess => success.tree
        case failure: ImplicitSearchFailure => report.throwError(failure.explanation)
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
  }
}
