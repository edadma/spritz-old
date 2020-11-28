name := "spritz"

version := "0.2.1"

scalaVersion := "2.13.3"

scalacOptions ++= Seq( "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions", "-language:existentials" )

organization := "xyz.hyperreal"

//resolvers += Resolver.sonatypeRepo( "snapshots" )

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Hyperreal Repository" at "https://dl.bintray.com/edadma/maven"

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "3.0.8" % "test",
	"org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
)

libraryDependencies ++= Seq(
//	"org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.0",
//	"org.scala-lang.modules" %% "scala-xml" % "1.0.6"
//	"org.scala-lang.modules" %% "scala-swing" % "2.0.3"
)

//libraryDependencies ++= Seq(
//  "com.typesafe" % "config" % "1.3.3"
//)

//libraryDependencies ++= Seq(
//  "jline" % "jline" % "2.14.6"
//)

libraryDependencies ++= Seq(
  "xyz.hyperreal" %% "yaml" % "0.2",
  "xyz.hyperreal" %% "args" % "0.2"
)

libraryDependencies ++= Seq(
  "org.apache.httpcomponents" % "httpcore-nio" % "4.4.13"
)

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.21"
)

libraryDependencies ++= {
  val akkaV = "2.6.10"
  Seq(
    "com.typesafe.akka" %% "akka-actor"    % akkaV,
    "com.typesafe.akka" %% "akka-remote"   % akkaV,
    "com.typesafe.akka" %% "akka-testkit"  % akkaV    % "test"
  )
}

libraryDependencies ++= {
  val akka_http = "10.2.1"
  Seq(
    "com.typesafe.akka" %% "akka-http-core"       % akka_http,
    "com.typesafe.akka" %% "akka-http"            % akka_http,
    "com.typesafe.akka" %% "akka-http-testkit"    % akka_http,
    "com.typesafe.akka" %% "akka-http-spray-json" % akka_http,
    "com.typesafe.akka" %% "akka-http-jackson"    % akka_http
  )
}

coverageExcludedPackages := ".*Main"

mainClass in (Compile, run) := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

mainClass in assembly := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

assemblyJarName in assembly := name.value + "-" + version.value + ".jar"

publishMavenStyle := true

publishArtifact in Test := false

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
