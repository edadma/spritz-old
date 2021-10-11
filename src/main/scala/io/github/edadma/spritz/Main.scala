package io.github.edadma.spritz

import java.nio.file.{Files, Paths}
import scopt.OParser

object Main extends App {
  val builder = OParser.builder[Args]
  val parser = {
    import builder._

    val BOLD = Console.BOLD
    var firstSection = true

    def section(name: String) = {
      val res =
        s"${if (!firstSection) "\n" else ""}$BOLD\u2501\u2501\u2501\u2501\u2501 $name ${"\u2501" * (20 - name.length)}${Console.RESET}"

      firstSection = false
      res
    }

    OParser.sequence(
      programName("spritz"),
      head("Spritz Static File Server", "v0.1.0"),
      arg[String]("<docroot>")
        .required()
        .action((d, c) => c.copy(docroot = (Paths get d).toAbsolutePath.normalize))
        .validate { d =>
          val p = (Paths get d).toAbsolutePath.normalize

          if (Files.exists(p) && Files.isReadable(p) && Files.isDirectory(p)) success
          else failure(s"docroot must be a readable directory: $p")
        }
        .text("path to document root"),
      opt[Int]('p', "port")
        .action((p, c) => c.copy(port = p))
        .validate(p => if (0 < p && p <= 0xffff) success else failure(s"invalid port number: $p"))
        .text("verbose output"),
      help('h', "help").text("prints this usage text"),
      opt[Unit]('v', "verbose")
        .action((_, c) => c.copy(verbose = true))
        .text("verbose output"),
      version("version").text("prints the version")
    )
  }

  OParser.parse(parser, args, Args(docroot = null, port = 8080, verbose = false)) match {
    case Some(Args(null, _, _)) => println(OParser.usage(parser))
    case Some(args)             => App run args
    case _                      =>
  }

}
