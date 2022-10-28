# union-derivation

[![Build Status](https://github.com/iRevive/union-derivation/workflows/CI/badge.svg)](https://github.com/iRevive/union-derivation/actions)
[![union-derivation-core Scala version support](https://index.scala-lang.org/irevive/union-derivation/union-derivation-core/latest-by-scala-version.svg)](https://index.scala-lang.org/irevive/union-derivation/union-derivation-core)

A micro-library to derive a typeclass for Scala 3 [Union types](https://docs.scala-lang.org/scala3/reference/new-types/union-types.html).

## Getting started

To use `union-derivation` in an existing SBT project with Scala **3.1.2** or a later version, add the following configuration to your `build.sbt`:

```scala
libraryDependencies += "io.github.irevive" %% "union-derivation-core" % "@VERSION@"
scalacOptions += "-Yretain-trees" // important for the detection of an abstract method in a trait
```

Versions matrix:

| Scala  | Library | JVM | Scala Native | Scala.js |
|:------:|:-------:|:---:|:------------:|:--------:|
| 3.1.2  |  0.0.3  |  +  |      -       |    -     |
| 3.2.0+ | 0.0.4+  |  +  |      +       |    -     |

## Usage example

### Typeclass definition

```scala mdoc:silent
import io.github.irevive.union.derivation.{IsUnion, UnionDerivation}

import scala.compiletime.{erasedValue, summonInline}
import scala.deriving.*

trait Show[A] {
  def show(a: A): String
}

object Show extends ShowLowPriority {

  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  inline given derived[A](using m: Mirror.Of[A]): Show[A] = { // (1)
    val elemInstances = summonAll[m.MirroredElemTypes]
    inline m match {
      case s: Mirror.SumOf[A]     => showSum(s, elemInstances)
      case p: Mirror.ProductOf[A] => showProduct(p, elemInstances)
    }
  }

  inline def summonAll[A <: Tuple]: List[Show[?]] =
    inline erasedValue[A] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Show[t]] :: summonAll[ts]
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

        product.productIterator
          .zip(product.productElementNames)
          .zip(elems.iterator)
          .map { case ((field, name), show) => s"$name = ${showA[Any](field, show)}" }
          .mkString(product.productPrefix + "(", ", ", ")")
      }
    }

}

trait ShowLowPriority {
  inline given derivedUnion[A](using IsUnion[A]): Show[A] = UnionDerivation.derive[Show, A] // (2)
}
```

1) The derivation mechanism. Checkout [Scala 3 docs](https://docs.scala-lang.org/scala3/reference/contextual/derivation.html) for more details.
2) `derivedUnion` has `IsUnion` constraint, therefore the method can be applied only to Union types. 

### Usage

```scala mdoc
type UnionType = Int | Long | String
final case class User(name: String, age: Long, flags: UnionType)

val unionShow: Show[UnionType] = summon[Show[UnionType]]
val userShow: Show[User] = summon[Show[User]]

println(unionShow.show(1))
println(unionShow.show(2L))
println(unionShow.show("3"))
println(userShow.show(User("Pablo", 22, 12L)))
println(userShow.show(User("Pablo", 33, 1)))
```

## Generated code

The library creates a set of if-else statements for the known types of the union.

The simplified version of the generated code:
```scala
val instance: Show[Int | String | Long] = UnionDerivation.derive[Show, Int | String | Long]

// expands into
val instance: Show[Int | String | Long] = { (value: Int | String | Long) =>
  if (value.isInstanceOf[Int]) summon[Show[Int]].show(value)
  else if (value.isInstanceOf[String]) summon[Show[String]].show(value)
  else if (value.isInstanceOf[Long]) summon[Show[Long]].show(value)
  else sys.error("Impossible")
}
```
