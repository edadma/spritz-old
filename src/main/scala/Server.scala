//@
package xyz.hyperreal.spritz

import scala.jdk.CollectionConverters._
import java.nio.file.{Files, Path}
import java.net.URLDecoder
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import org.apache.http.{
  ExceptionLogger,
  HttpConnection,
  HttpRequest,
  HttpResponse,
  HttpStatus,
  MethodNotSupportedException
}
import org.apache.http.entity.ContentType
import org.apache.http.impl.nio.bootstrap.ServerBootstrap
import org.apache.http.impl.nio.reactor.IOReactorConfig
import org.apache.http.nio.entity.{NFileEntity, NStringEntity}
import org.apache.http.nio.protocol.{
  BasicAsyncRequestConsumer,
  BasicAsyncResponseProducer,
  HttpAsyncExchange,
  HttpAsyncRequestConsumer,
  HttpAsyncRequestHandler
}
import org.apache.http.protocol.{HttpContext, HttpCoreContext}

class Server(val docRoot: Path, val port: Int) {

  require(
    Files.exists(docRoot) && Files.isDirectory(docRoot) && Files.isReadable(
      docRoot),
    s"Document root must be an accessible directory")

  private val rootdir = docRoot.toAbsolutePath.normalize
  private val sslContext = null
  private val config =
    IOReactorConfig.custom.setSoTimeout(15000).setTcpNoDelay(true).build
  private val server =
    ServerBootstrap.bootstrap
      .setListenerPort(port)
      .setServerInfo("Spritz/0.1")
      .setIOReactorConfig(config)
      .setSslContext(sslContext)
      .setExceptionLogger(ExceptionLogger.STD_ERR)
      .registerHandler("*", new HttpFileHandler(rootdir))
      .create

  private val modifiedFormatter =
    DateTimeFormatter.ofPattern("dd-MM-yy HH:mm")

  def start(): Unit = {
    server.start()
    println(s"Serving $rootdir on ${server.getEndpoint.getAddress}")

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        shutdown()
      }
    })
  }

  def shutdown(): Unit = server.shutdown(5, TimeUnit.SECONDS)

  def await(): Unit = server.awaitTermination(Long.MaxValue, TimeUnit.DAYS)

  private class HttpFileHandler(val rootdir: Path)
      extends HttpAsyncRequestHandler[HttpRequest] {
    def processRequest(
        request: HttpRequest,
        context: HttpContext): HttpAsyncRequestConsumer[HttpRequest] = {
      new BasicAsyncRequestConsumer
    }

    def handle(request: HttpRequest,
               httpexchange: HttpAsyncExchange,
               context: HttpContext): Unit = {
      val response = httpexchange.getResponse

      handleInternal(request, response, context)
      httpexchange.submitResponse(new BasicAsyncResponseProducer(response))
    }

    private def handleInternal(request: HttpRequest,
                               response: HttpResponse,
                               context: HttpContext): Unit = {
      val method = request.getRequestLine.getMethod.toUpperCase

      if (!(method == "GET") && !(method == "HEAD") && !(method == "POST"))
        throw new MethodNotSupportedException(method + " method not supported")

      val uri = request.getRequestLine.getUri
      val file = rootdir resolve (URLDecoder.decode(uri, "UTF-8") drop 1)
      val index = file resolve "index"

      if (!Files.exists(file)) {
        val html = file.getParent resolve s"${file.getFileName}.html"

        if (isFile(html))
          serveOK(html)
        else {
          val file404 = rootdir resolve "404.html"

          if (isFile(file404))
            serve(file404, HttpStatus.SC_NOT_FOUND)
          else {
            response setStatusCode HttpStatus.SC_NOT_FOUND

            val entity = new NStringEntity(
              s"<html><body><h1>File $file not found</h1></body></html>",
              ContentType.create("text/html", "UTF-8"))

            response.setEntity(entity)
          }

          println(s"File $file not found")
        }
      } else if (Files.isDirectory(file) && isFile(index)) {
        serveOK(index)
      } else if (!Files.isReadable(file))
        denied(file)
      else if (Files.isDirectory(file))
        serveListing(file)
      else
        serveOK(file)

      def serveOK(f: Path): Unit = serve(f, HttpStatus.SC_OK)

      def serve(f: Path, sc: Int): Unit = {
        val coreContext = HttpCoreContext.adapt(context)
        val conn = coreContext.getConnection(classOf[HttpConnection])

        response.setStatusCode(sc)

        val typ =
          Files.probeContentType(f) match {
            case null => ContentType.APPLICATION_OCTET_STREAM
            case t    => ContentType.create(t)
          }

        val body = new NFileEntity(f.toFile, typ)

        response.setEntity(body)
        println(s"$conn: serving file $f - $sc")
      }

      def isFile(f: Path) =
        Files.exists(f) && Files.isReadable(f) && Files.isRegularFile(f)

      def denied(path: Path): Unit = {
        val file403 = rootdir resolve "403.html"

        if (isFile(file403))
          serve(file403, HttpStatus.SC_FORBIDDEN)
        else {
          response.setStatusCode(HttpStatus.SC_FORBIDDEN)

          val entity = new NStringEntity(
            "<html><body><h1>Access denied</h1></body></html>",
            ContentType.create("text/html", "UTF-8"))

          response.setEntity(entity)
        }

        println(s"Cannot read file: $file")
      }

      def serveListing(path: Path): Unit = {
        val buf = new StringBuilder

        for (p <- Files.list(path).iterator().asScala) {
          val rel = docRoot relativize p
          println(p, rel)
          val name = p.getFileName
          val modified =
            modifiedFormatter.format(
              (Files getLastModifiedTime p toInstant)
                .atZone(ZoneId.systemDefault))
          val size = Files size p

          buf ++= s"""<tr><td><a href="/$rel">$name</a></td><td>$modified</td><td>$size</td></tr>"""
        }

        val listing =
          s"""
             |<html>
             |  <body>
             |    <h1>Index of /${docRoot relativize path}</h1>
             |
             |    <table>
             |      <tr>
             |        <th>Name</th>
             |        <th>Last modified</th>
             |        <th>Size</th>
             |      </tr>
             |      $buf
             |    </table>
             |  </body>
             |</html>
             |""".stripMargin

        response.setStatusCode(HttpStatus.SC_OK)

        val entity =
          new NStringEntity(listing, ContentType.create("text/html", "UTF-8"))

        response.setEntity(entity)

      }
    }
  }

}
