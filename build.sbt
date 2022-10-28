ThisBuild / scalaVersion := "3.2.0"

ThisBuild / githubWorkflowTargetBranches        := Seq("main")
ThisBuild / githubWorkflowTargetTags           ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v")))

ThisBuild / githubWorkflowBuildPostamble ++= Seq(
  WorkflowStep.Sbt(List("docs/mdoc"), name = Some("Generate documentation"))
)

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)

lazy val `union-derivation` = project
  .in(file("."))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .aggregate(core, examples)

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name                := "union-derivation-core",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )

lazy val examples = project
  .in(file("modules/examples"))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name := "union-derivation-examples"
  )
  .dependsOn(core)

lazy val docs = project
  .in(file("modules/docs"))
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name    := "union-derivation-docs",
    mdocIn  := baseDirectory.value / "src" / "main" / "mdoc" / "index.md",
    mdocOut := file("README.md"),
    mdocVariables := Map(
      "VERSION" -> version.value.replaceFirst("\\+.*", "")
    )
  )
  .dependsOn(core)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-source:future",
    "-no-indent", // let's be conservative for a while
    "-old-syntax",
    "-Yretain-trees"
  )
)

lazy val noPublishSettings = Seq(
  publish         := {},
  publishLocal    := {},
  publishArtifact := false,
  publish / skip  := true
)

inThisBuild(
  Seq(
    organization := "io.github.irevive",
    homepage     := Some(url("https://github.com/iRevive/union-derivation")),
    licenses     := List(License.MIT),
    developers   := List(Developer("iRevive", "Maksym Ochenashko", "", url("https://github.com/iRevive")))
  )
)
