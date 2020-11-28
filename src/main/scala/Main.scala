package xyz.hyperreal.spritz

import java.nio.file.Paths

object Main extends App {

  if (args.length < 1) {
    System.err.println("Please specify document root directory")
    System.exit(1)
  }

  val server = new Server(Paths get args(0), 8080)

  server.start()
  server.await()

}
