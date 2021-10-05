import io.github.irevive.union.derivation.{IsUnion, UnionDerivation}

import scala.compiletime.{erasedValue, summonInline}
import scala.deriving.*

trait Show[A] {
  def show(a: A): String
}

object Show extends LowPriority {

  def apply[E](using ev: Show[E]): Show[E] = ev

  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  inline def deriveUnion[A]: Show[A] = UnionDerivation.derive[Show, A]

  inline given derived[A](using m: Mirror.Of[A]): Show[A] = {
    val elemInstances = summonAll[m.MirroredElemTypes]
    inline m match {
      case s: Mirror.SumOf[A] => showSum(s, elemInstances)
      case p: Mirror.ProductOf[A] => showProduct(p, elemInstances)
    }
  }

  private inline def summonAll[A <: Tuple]: List[Show[?]] = {
    inline erasedValue[A] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonInline[Show[t]] :: summonAll[ts]
    }
  }

  private def showA[A](a: A, show: Show[?]): String =
    show.asInstanceOf[Show[A]].show(a)

  private def showSum[A](s: Mirror.SumOf[A], elems: => List[Show[?]]): Show[A] =
    new Show[A] {
      def show(a: A): String = showA(a, elems(s.ordinal(a)))
    }

  private def showProduct[A](p: Mirror.ProductOf[A], elems: => List[Show[?]]): Show[A] =
    new Show[A] {
      def show(a: A): String = {
        val product = a.asInstanceOf[Product]

        product.productIterator.zip(product.productElementNames).zip(elems.iterator)
          .map { case ((field, name), show) => s"$name = ${showA[Any](field, show)}" }
          .mkString(product.productPrefix + "(", ", ", ")")
      }
    }

}

private trait LowPriority {
  inline given derivedUnion[A](using IsUnion[A]): Show[A] = UnionDerivation.derive[Show, A]
}