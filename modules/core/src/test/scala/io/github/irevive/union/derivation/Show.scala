package io.github.irevive.union.derivation

trait Show[A] {
  def show(a: A): String
}

object Show {

  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  inline def deriveUnion[A]: Show[A] = ${ Union.deriveImpl[A] }

  object Union extends UnionDerivation[Show] {
    import scala.quoted.*

    protected def derivationMacro(using Quotes, Type[Show]): UnionDerivation.Macro[Show] =
      new UnionDerivation.Macro[Show] {
        import quotes.reflect.*

        protected def generate[A: Type](knownTypes: List[TypeRepr]): Expr[Show[A]] =
          '{ value => ${ body[A, String]('{ value }, knownTypes.map(_.typeSymbol)) } }

        protected def applyTypeclass(tcl: Term, arg: Term): Term =
          Apply(Select.unique(tcl, "show"), arg :: Nil)
      }
  }
}
