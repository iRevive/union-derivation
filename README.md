# union-derivation

[![Continuous Integration](https://github.com/iRevive/union-derivation/actions/workflows/ci.yml/badge.svg)](https://github.com/iRevive/union-derivation/actions/workflows/ci.yml)
[![union-derivation-core Scala version support](https://index.scala-lang.org/irevive/union-derivation/union-derivation-core/latest.svg)](https://index.scala-lang.org/irevive/union-derivation/union-derivation-core)

A micro-library to derive a typeclass for Scala 3 [Union types](https://docs.scala-lang.org/scala3/reference/new-types/union-types.html).

<!-- TOC -->
* [Getting started](#getting-started)
* [How it works](#how-it-works)
* [Usage example](#usage-example)
  * [1. Derivation of a typeclass for a union type](#1-derivation-of-a-typeclass-for-a-union-type)
  * [2. Derivation of a typeclass with an extension method](#2-derivation-of-a-typeclass-with-an-extension-method)
  * [3. Full typeclass derivation](#3-full-typeclass-derivation)
    * [3.1. Typeclass definition](#31-typeclass-definition)
    * [3.2. Usage](#32-usage)
    * [3.3. Automated derivation via `derives`](#33-automated-derivation-via-derives)
* [Unsupported cases](#unsupported-cases)
  * [A typeclass without a polymorphic parameter](#a-typeclass-without-a-polymorphic-parameter)
  * [A typeclass with multiple type parameters](#a-typeclass-with-multiple-type-parameters)
  * [A typeclass with multiple polymorphic parameters of the same type](#a-typeclass-with-multiple-polymorphic-parameters-of-the-same-type)
  * [A typeclass with a curried function](#a-typeclass-with-a-curried-function)
* [scala-cli](#scala-cli)
<!-- TOC -->

## Getting started

To use `union-derivation` in an existing SBT project with Scala **3.3.1** or a later version. 

Configure you project via `build.sbt`:

```scala
libraryDependencies += "io.github.irevive" %% "union-derivation-core" % "0.2.0"
scalacOptions += "-Yretain-trees" // important for the detection of an abstract method in a trait
```

Or via [scala-cli](https://scala-cli.virtuslab.org/) directives:
```scala
//> using scala "3.3.4"
//> using lib "io.github.irevive::union-derivation-core:0.2.0"
//> using options "-Yretain-trees" // important for the detection of an abstract method in a trait
```

Versions matrix:

| Scala  | Library | JVM | Scala Native (0.4) | Scala Native (0.5.x) | Scala.js |
|:------:|:-------:|:---:|:------------------:|:---------------------:|:--------:|
| 3.1.2  |  0.0.3  |  +  |         -          |           -           |    -     |
| 3.2.0+ | 0.0.4+  |  +  |         +          |           +           |    -     |
| 3.3.x  |  0.1.x  |  +  |         +          |           -           |    +     |
| 3.3.x  |  0.2.x  |  +  |         -          |           +           |    +     |

## How it works

The library generates a set of if-else statements for the known types of the union.

The simplified version of the generated code:
```scala
val instance: Show[Int | String | Long] = UnionDerivation.derive[Show, Int | String | Long]

// expands into
val instance: Show[Int | String | Long] = { (value: Int | String | Long) =>
  if (value.isInstanceOf[Int]) summon[Show[Int]].show(value.asInstanceOf[Int])
  else if (value.isInstanceOf[String]) summon[Show[String]].show(value.asInstanceOf[String])
  else if (value.isInstanceOf[Long]) summon[Show[Long]].show(value.asInstanceOf[Long])
  else sys.error("Impossible")
}
```

## Usage example

### 1. Derivation of a typeclass for a union type

```scala
import io.github.irevive.union.derivation.{IsUnion, UnionDerivation}

// A typeclass definition
trait Show[A] { 
  def show(value: A): String
}

// The typeclass instances
given Show[String] = value => s"str: $value"
given Show[Int]    = value => s"int: $value"

// Implicit derivation that works only for the union types
inline given derivedUnion[A](using IsUnion[A]): Show[A] = 
  UnionDerivation.derive[Show, A]

println(summon[Show[String | Int]].show(1))
// int: 1
println(summon[Show[String | Int]].show("1"))
// str: 1
```

### 2. Derivation of a typeclass with an extension method

A derivation works for a typeclass with a single extension method too:
```scala
import io.github.irevive.union.derivation.UnionDerivation

// A typeclass definition
trait Show[A] {
  extension(a: A) def show: String
}

// The typeclass instances
given Show[String] = value => s"str: $value"
given Show[Int]    = value => s"int: $value"

// Explicit (manual) derivation for the specific union type
type UnionType = String | Int
given Show[UnionType] = UnionDerivation.derive[Show, UnionType]

println((1: UnionType).show)
// int: 1
println(("1": UnionType).show)
// str: 1
```

### 3. Full typeclass derivation

#### 3.1. Typeclass definition

```scala
import io.github.irevive.union.derivation.{IsUnion, UnionDerivation}

import scala.compiletime.{erasedValue, summonInline}
import scala.deriving.*

// A typeclass definition
trait Show[A] { 
  def show(a: A): String
}

object Show extends ShowLowPriority {
  
  def apply[A](using ev: Show[A]): Show[A] = ev

  // The typeclass instances
  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  // The derivation mechanism
  // Checkout https://docs.scala-lang.org/scala3/reference/contextual/derivation.html for more details
  inline given derived[A](using m: Mirror.Of[A]): Show[A] = {
    val elemInstances = summonAll[m.MirroredElemTypes]
    inline m match {
      case s: Mirror.SumOf[A]     => showSum(s, elemInstances)
      case _: Mirror.ProductOf[A] => showProduct(elemInstances)
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

  private def showProduct[A](elems: => List[Show[?]]): Show[A] = 
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

// Since the 'derivedUnion' is defined in the trait, it's a low-priority implicit
trait ShowLowPriority {
  // Implicit derivation that works only for the union types
  inline given derivedUnion[A](using IsUnion[A]): Show[A] = UnionDerivation.derive[Show, A]
}
```

#### 3.2. Usage

```scala
type UnionType = Int | Long | String
final case class User(name: String, age: Long, flags: UnionType)

val unionShow: Show[UnionType] = summon[Show[UnionType]]
// unionShow: Show[UnionType] = repl.MdocSession$MdocApp6$$Lambda/0x0000008003871340@75308f03
val userShow: Show[User] = summon[Show[User]]
// userShow: Show[User] = repl.MdocSession$$anon$18@972b92

println(unionShow.show(1))
// Int(1)
println(unionShow.show(2L))
// Long(2)
println(unionShow.show("3"))
// String(3)
println(userShow.show(User("Pablo", 22, 12L)))
// User(name = String(Pablo), age = Long(22), flags = Long(12))
println(userShow.show(User("Pablo", 33, 1)))
// User(name = String(Pablo), age = Long(33), flags = Int(1))
```

#### 3.3. Automated derivation via `derives`

```scala
final case class Author(name: String, age: Long, flags: Long | String) derives Show
println(Show[Author].show(Author("Pablo", 22, 12L)))
// Author(name = String(Pablo), age = Long(22), flags = Long(12))
println(Show[Author].show(Author("Pablo", 33, "string flag")))
// Author(name = String(Pablo), age = Long(33), flags = String(string flag))
```

## Unsupported cases

### A typeclass without a polymorphic parameter

A typeclass function without parameters:
```scala
trait Typeclass[A] {
  def magic: String
  //       ^
  // Polymorphic parameter of type A is missing
}
```

A typeclass function without polymorphic parameter:
```scala
trait Typeclass[A] {
  def magic(a: Int): String
  //              ^
  // Polymorphic parameter of type A is missing
}
```

A polymorphic parameter is mandatory to perform the type matching in runtime.

### A typeclass with multiple type parameters

```scala
trait Typeclass[A, B] {
  def magic(a: A, b: B): String
}
```

However, you can overcome this limitation by using 
[polymorphic function types](https://docs.scala-lang.org/scala3/reference/new-types/polymorphic-function-types.html):
```scala
trait Typeclass[A] {
  def magic(a: A): [B] => B => String
}
```

### A typeclass with multiple polymorphic parameters of the same type

```scala
trait Typeclass[A] {
  def magic(a1: A, b: Int, a2: A): String
}
```

A polymorphic parameter of type `A` appears in two positions. A macro cannot properly detect which type to use.

### A typeclass with a curried function

```scala
trait Typeclass[A] {
  def magic(a: A)(b: String): A
}
```

However, you can overcome this limitation by moving currying to the result type definition:
```scala
trait Typeclass[A] {
  def magic(a: A): String => A
}
```


## scala-cli

The library works out of the box with [scala-cli](https://scala-cli.virtuslab.org/) too.

```scala
//> using scala "3.3.4"
//> using lib "io.github.irevive::union-derivation-core:0.2.0"
//> using options "-Yretain-trees"

import io.github.irevive.union.derivation.{IsUnion, UnionDerivation}

trait Show[A] {
  def show(value: A): String
}

given Show[String] = value => s"str: $value"
given Show[Int]    = value => s"int: $value"

inline given derivedUnion[A](using IsUnion[A]): Show[A] = UnionDerivation.derive[Show, A]

println(summon[Show[String | Int]].show(1))
// int: 1
println(summon[Show[String | Int]].show("1"))
// str: 1
```
