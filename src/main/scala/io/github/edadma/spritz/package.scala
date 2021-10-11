package io.github.edadma

import java.nio.file.Path

package object spritz {

  case class Args(docroot: Path, port: Int, verbose: Boolean)

}
