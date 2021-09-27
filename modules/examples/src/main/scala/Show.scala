import io.github.irevive.union.derivation.UnionDerivation

trait Show[A] {
  def show(a: A): String
}

object Show {

  def apply[E](using ev: Show[E]): Show[E] = ev

  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  inline def deriveUnion[A]: Show[A] = UnionDerivation.derive[Show, A]

}
