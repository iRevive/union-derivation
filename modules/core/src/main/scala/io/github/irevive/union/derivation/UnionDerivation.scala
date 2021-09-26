package io.github.irevive.union.derivation

import scala.quoted.*

abstract class UnionDerivation[F[_]] {

  def deriveImpl[A: Type](using Quotes, Type[F]): Expr[F[A]] = {
    val impl = derivationMacro
    impl.deriveImpl
  }

  protected def derivationMacro(using Quotes, Type[F]): UnionDerivation.Macro[F]

}

object UnionDerivation {

  abstract class Macro[F[_]: Type](using val quotes: Quotes) {
    import quotes.reflect.*

    def deriveImpl[A: Type]: Expr[F[A]] = {
      val tpe: TypeRepr = TypeRepr.of[A]

      tpe.dealias match {
        case o: OrType => generate[A](collectTypes(o))
        case other     => sys.error("Only Union type supported")
      }
    }

    protected def applyTypeclass(tcl: Term, arg: Term): Term

    protected def generate[A: Type](knownTypes: List[TypeRepr]): Expr[F[A]]

    protected def body[A, R: Type](t: Expr[A], knownTypes: List[Symbol]): Expr[R] = {
      val selector: Term = t.asTerm

      val ifBranches: List[(Term, Term)] = knownTypes.map { sym =>
        val childTpe: TypeTree = TypeIdent(sym)
        val condition: Term = TypeApply(
          Select.unique(selector, "isInstanceOf"),
          childTpe :: Nil
        )

        val action: Term =
          applyTypeclass(
            lookupImplicit(childTpe.tpe),
            Select.unique(selector, "asInstanceOf").appliedToType(childTpe.tpe)
          )

        (condition, action)
      }

      mkIfStatement(ifBranches).asExprOf[R]
    }

    def collectTypes(start: OrType): List[TypeRepr] = {
      def loop(tpe: TypeRepr, output: List[TypeRepr]): List[TypeRepr] =
        tpe.dealias match {
          case o: OrType => loop(o.left, output) ::: loop(o.right, output) ::: output
          case r         => r +: output
        }

      loop(start, Nil)
    }

    def lookupImplicit(t: TypeRepr): Term = {
      val typeclassTpe = TypeRepr.of[F]
      val tclTpe       = typeclassTpe.appliedTo(t)
      Implicits.search(tclTpe) match {
        case res: ImplicitSearchSuccess => res.tree
        case other                      => sys.error(s"Cannot find ${typeclassTpe.show} implicit for ${t.show}")
      }
    }

    def mkIfStatement(branches: List[(Term, Term)]): Term =
      branches match {
        case (p1, a1) :: xs =>
          If(p1, a1, mkIfStatement(xs))
        case Nil =>
          ('{ throw RuntimeException(s"Unhandled condition encountered during derivation") }).asTerm
      }
  }
}
