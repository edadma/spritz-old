//@
package xyz.hyperreal.spritz

import scala.jdk.CollectionConverters._
import java.nio.file.{Files, Path}
import java.net.{URLDecoder, URLEncoder}
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

  val VERSION = "Spritz/0.1"
  private val rootdir = docRoot.toAbsolutePath.normalize
  private val sslContext = null
  private val config =
    IOReactorConfig.custom.setSoTimeout(15000).setTcpNoDelay(true).build
  private val server =
    ServerBootstrap.bootstrap
      .setListenerPort(port)
      .setServerInfo(VERSION)
      .setIOReactorConfig(config)
      .setSslContext(sslContext)
      .setExceptionLogger(ExceptionLogger.STD_ERR)
      .registerHandler("*", new HttpFileHandler(rootdir))
      .create

  private val modifiedFormatter =
    DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")

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
      val coreContext = HttpCoreContext.adapt(context)
      val conn = coreContext.getConnection(classOf[HttpConnection])
      val method = request.getRequestLine.getMethod.toUpperCase

      if (!(method == "GET") && !(method == "HEAD") && !(method == "POST"))
        throw new MethodNotSupportedException(method + " method not supported")

      val uri = request.getRequestLine.getUri
      val path = URLDecoder.decode(uri, "UTF-8") drop 1
      val file = rootdir resolve path
      val index = file resolve "index"

      if (path startsWith "*mimetype/")
        serveMimeIcon()
      else if (path == "*shutdown")
        shutdown()
      else if (!Files.exists(file)) {
        val html = file.getParent resolve s"${file.getFileName}.html"

        if (isFile(html))
          serveOK(html)
        else
          notFound()
      } else if (Files.isDirectory(file) && isFile(index)) {
        serveOK(index)
      } else if (!Files.isReadable(file))
        forbidden()
      else if (Files.isDirectory(file))
        serveListing(file)
      else
        serveOK(file)

      def serveMimeIcon(): Unit = {
        val Array(_, a, b) = path.split("/")
        val file1 =
          Path.of("/usr/share/icons/oxygen/base/16x16/mimetypes", s"$a-$b.png")
        val file2 = Path.of("/usr/share/icons/oxygen/base/16x16/mimetypes",
                            s"$a-x-$b.png")

        if (Files.exists(file1))
          serveOK(file1)
        else if (Files.exists(file2))
          serveOK(file2)
        else
          serveOK(Path.of(
            "/usr/share/icons/oxygen/base/16x16/mimetypes/application-octet-stream.png"))
      }

      def serveOK(f: Path): Unit = serve(f, HttpStatus.SC_OK)

      def serve(f: Path, sc: Int): Unit = {
        val typ =
          Files.probeContentType(f) match {
            case null => ContentType.APPLICATION_OCTET_STREAM
            case t    => ContentType.create(t)
          }
        val body = new NFileEntity(f.toFile, typ)

        response.setStatusCode(sc)
        response.setEntity(body)
        println(s"$conn: $f - $sc - $typ")
      }

      def isFile(f: Path) =
        Files.exists(f) && Files.isReadable(f) && Files.isRegularFile(f)

      def forbidden(): Unit = {
        val file403 = rootdir resolve "403.html"

        if (isFile(file403))
          serve(file403, HttpStatus.SC_FORBIDDEN)
        else {
          response.setStatusCode(HttpStatus.SC_FORBIDDEN)

          val entity = new NStringEntity(
            "<html><body><h1>Forbidden</h1></body></html>",
            ContentType.create("text/html", "UTF-8"))

          response.setEntity(entity)
        }

        println(s"$conn: $file - 403")
      }

      def notFound(): Unit = {
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

        println(s"$conn: $file - 404")
      }

      def serveListing(path: Path): Unit = {
        val buf = new StringBuilder

        for (p <- Files
               .list(path)
               .iterator()
               .asScala
               .toList
               .sorted) {
          val rel = docRoot relativize p
          val href = URLEncoder.encode(rel.toString, "UTF-8")
          val icon =
            Files.probeContentType(p) match {
              case null => "application/octet-stream"
              case t    => t
            }
          val name =
            if (Files.isDirectory(p)) s"${p.getFileName}/" else p.getFileName
          val modified =
            modifiedFormatter
              .format(
                (Files getLastModifiedTime p toInstant)
                  .atZone(ZoneId.systemDefault))
              .replace(".", "")
          val size =
            if (Files.isDirectory(p)) "-"
            else {
              val n = Files size p

              if (n < 1024)
                n.toString
              else if (n < 1048576)
                (n / 1000.0).formatted("%.1fK")
              else if (n < 1073741824)
                (n / 1000000.0).formatted("%.1fM")
              else
                (n / 1000000000.0).formatted("%.1fG")
            }

          buf ++=
            s"""
               |      <tr>
               |        <td><img src="/*mimetype/$icon"> <a href="/$href">$name</a></td>
               |        <td>$modified</td>
               |        <td align='right'>$size</td>
               |      </tr>
               |""".stripMargin
        }

        val listing =
          s"""<!DOCTYPE html>
             |<html>
             |  <head>
             |    <style>
             |      table {
             |        font-family: monospaced;
             |      }
             |
             |      td, th {
             |        padding-left: 10px;
             |        padding-right: 10px;
             |      }
             |
             |      tr:nth-child(even) {
             |        background-color: #eeeeee;
             |      }
             |    </style>
             |  </head>
             |
             |  <body>
             |    <h2>Index of /${docRoot relativize path}</h2>
             |
             |    <table>
             |      <tr>
             |        <th>Name</th>
             |        <th>Last modified</th>
             |        <th>Size</th>
             |      </tr>
             |      $buf
             |    </table>
             |
             |    <hr />
             |
             |    <p>$VERSION at localhost port $port</p>
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
