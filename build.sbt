name := "spritz"

version := "0.1.0"

scalaVersion := "2.13.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:postfixOps",
  "-language:implicitConversions",
  "-language:existentials"
)

organization := "io.github.edadma"

githubOwner := "edadma"

githubRepository := name.value

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.9" % "test"
)

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "4.0.1",
  "org.apache.httpcomponents" % "httpcore-nio" % "4.4.14"
)

libraryDependencies ++= Seq(
  "io.github.edadma" %% "yaml" % "0.1.11"
)

mainClass := Some("io.github.edadma." + name.value.replace('-', '_') + ".Main")

assembly / mainClass := Some("io.github.edadma." + name.value.replace('-', '_') + ".Main")

assembly / assemblyJarName := name.value + "-" + version.value + ".jar"

publishMavenStyle := true

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

licenses := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))

homepage := Some(url("https://github.com/edadma/" + name.value))

pomExtra :=
  <scm>
    <url>git@github.com:edadma/{name.value}.git</url>
    <connection>scm:git:git@github.com:edadma/{name.value}.git</connection>
  </scm>
  <developers>
    <developer>
      <id>edadma</id>
      <name>Edward A. Maxedon, Sr.</name>
      <url>https://github.com/edadma</url>
    </developer>
  </developers>
