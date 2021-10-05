object Main {

  final case class User(name: String, age: Long, flags: Int | Long)

  def main(args: Array[String]): Unit = {
    type UnionType = Int | Long | String

    val show: Show[UnionType] = summon[Show[UnionType]]
    val userShow: Show[User] = summon[Show[User]]

    println(show.show(1))
    println(show.show(2L))
    println(show.show("3"))
    println(userShow.show(User("Pablo", 22, 12L)))
  }
}
