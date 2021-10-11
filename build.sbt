name := "spritz"

version := "0.2.2"

scalaVersion := "2.13.6"

scalacOptions ++= Seq( "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions", "-language:existentials" )

organization := "io.github.edadma"

//resolvers += Resolver.sonatypeRepo( "snapshots" )

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Hyperreal Repository" at "https://dl.bintray.com/edadma/maven"

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "3.2.9" % "test",
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
  "io.github.edadma" %% "yaml" % "0.1.11",
)

libraryDependencies ++= Seq(
  "org.apache.httpcomponents" % "httpcore-nio" % "4.4.14"
)

//libraryDependencies ++= Seq(
//  "org.slf4j" % "slf4j-simple" % "1.7.21"
//)

//libraryDependencies ++= {
//  val akkaV = "2.6.10"
//  Seq(
//    "com.typesafe.akka" %% "akka-actor"    % akkaV,
//    "com.typesafe.akka" %% "akka-remote"   % akkaV,
//    "com.typesafe.akka" %% "akka-testkit"  % akkaV    % "test"
//  )
//}

//libraryDependencies ++= {
//  val akka_http = "10.2.1"
//  Seq(
//    "com.typesafe.akka" %% "akka-http-core"       % akka_http,
//    "com.typesafe.akka" %% "akka-http"            % akka_http,
//    "com.typesafe.akka" %% "akka-http-testkit"    % akka_http,
//    "com.typesafe.akka" %% "akka-http-spray-json" % akka_http,
//    "com.typesafe.akka" %% "akka-http-jackson"    % akka_http
//  )
//}

mainClass := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

assembly / mainClass := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

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
