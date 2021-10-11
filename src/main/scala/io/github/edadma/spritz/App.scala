package io.github.edadma.spritz

object App {

  val run: PartialFunction[Args, Unit] = { case Args(docroot, port, verbose) =>
    val server = new Server(docroot, port, verbose)

    server.start()
    server.await()
  }

}
