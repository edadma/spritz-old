//@
package io.github.edadma.spritz

import scala.jdk.CollectionConverters._
import java.nio.file.{FileSystems, Files, Path}
import java.net.{URLDecoder, URLEncoder}
import java.time.{Instant, ZoneId}
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
import org.apache.http.nio.entity.{NByteArrayEntity, NFileEntity, NStringEntity}
import org.apache.http.nio.protocol.{
  BasicAsyncRequestConsumer,
  BasicAsyncResponseProducer,
  HttpAsyncExchange,
  HttpAsyncRequestConsumer,
  HttpAsyncRequestHandler
}
import org.apache.http.protocol.{HttpContext, HttpCoreContext}

class Server(val docRoot: Path, val port: Int, verbose: Boolean) {

  require(
    Files.exists(docRoot) && Files.isDirectory(docRoot) && Files.isReadable(docRoot),
    s"Document root must be an accessible directory"
  )

  val VERSION = "Spritz/0.1.0"
  private val docRootPath = docRoot.toAbsolutePath.normalize
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
      .registerHandler("*", new HttpFileHandler(docRootPath))
      .create

  private val modifiedFormatter =
    DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm")

  def start(): Unit = {
    server.start()
    println(s"Serving $docRootPath on ${server.getEndpoint.getAddress}")

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        shutdown()
      }
    })
  }

  def shutdown(): Unit = server.shutdown(1, TimeUnit.SECONDS)

  def await(): Unit = server.awaitTermination(Long.MaxValue, TimeUnit.DAYS)

  def readResource(name: String): Option[Array[Byte]] = {
    val is = getClass.getResourceAsStream(name)

    if (is ne null) {
      val data = is.readAllBytes()

      is.close()
      Some(data)
    } else None
  }

  private class HttpFileHandler(val handlerRoot: Path) extends HttpAsyncRequestHandler[HttpRequest] {
    def processRequest(request: HttpRequest, context: HttpContext): HttpAsyncRequestConsumer[HttpRequest] = {
      new BasicAsyncRequestConsumer
    }

    def handle(request: HttpRequest, httpexchange: HttpAsyncExchange, context: HttpContext): Unit = {
      val response = httpexchange.getResponse

      handleInternal(request, response, context)
      httpexchange.submitResponse(new BasicAsyncResponseProducer(response))
    }

    private def handleInternal(request: HttpRequest, response: HttpResponse, context: HttpContext): Unit = {
      val coreContext = HttpCoreContext.adapt(context)
      val conn = coreContext.getConnection(classOf[HttpConnection])
      val method = request.getRequestLine.getMethod.toUpperCase

      if (!(method == "GET") && !(method == "HEAD") && !(method == "POST"))
        throw new MethodNotSupportedException(method + " method not supported")

      val uri = request.getRequestLine.getUri
      val protocol = request.getRequestLine.getProtocolVersion
      val path = URLDecoder.decode(uri, "UTF-8")
      val file = handlerRoot resolve path.substring(1)
      val index = file resolve "index.html"

      if (path startsWith "/*mimetype/")
        serveMimeIcon()
      else if (path == "/*shutdown")
        shutdown()
      else if (isReadableFile(file))
        serveFile(file)
      else if (Files.isDirectory(file) && isReadableFile(index))
        serveFile(index)
      else if (Files.isDirectory(file) && Files.isReadable(file))
        serveListing()
      else if (Files.exists(file) && !Files.isReadable(file))
        serveForbidden()
      else
        serveNotFound()

      println(
        s"""$conn - ${Instant.now.toString} - "$method $path $protocol" - ${response.getStatusLine.getStatusCode} - ${response.getEntity.getContentLength} - ${response.getEntity.getContentType.toString drop 14}"""
      )

      // /usr/share/icons/oxygen/base/16x16/mimetypes
      def serveMimeIcon(): Unit = {
        val Array(_, _, a, b) = path.split("/")
        val file1 = s"mimetypes/$a-$b.png"
        val file2 = s"mimetypes/$a-x-$b.png"
        val file3 = "mimetypes/application-octet-stream.png"

        servePNG(readResource(file1) orElse (readResource(file2) orElse readResource(file3)))
      }

      def isReadableFile(f: Path) =
        Files.exists(f) && Files.isReadable(f) && Files.isRegularFile(f)

      def servePNG(png: Option[Array[Byte]]): Unit = {
        png match {
          case Some(data) =>
            response.setStatusCode(HttpStatus.SC_OK)
            response.setEntity(new NByteArrayEntity(data, ContentType.IMAGE_PNG))
          case None =>
            response setStatusCode HttpStatus.SC_NOT_FOUND
            response setEntity new NStringEntity("no image")
        }
      }

      def serve(f: Path, sc: Int): Unit = {
        val typ =
          Files.probeContentType(f) match {
            case null => ContentType.APPLICATION_OCTET_STREAM
            case t    => ContentType.create(t)
          }
        val body = new NFileEntity(f.toFile, typ)

        response.setStatusCode(sc)
        response.setEntity(body)
      }

      def serveFile(f: Path): Unit = serve(f, HttpStatus.SC_OK)

      def serveForbidden(): Unit = {
        val file403 = handlerRoot resolve "403.html"

        if (isReadableFile(file403))
          serve(file403, HttpStatus.SC_FORBIDDEN)
        else {
          response.setStatusCode(HttpStatus.SC_FORBIDDEN)

          val entity = new NStringEntity(
            s"""<!DOCTYPE html>
               |<html>
               | <header>
               |  <title>403 Forbidden</title>
               | </header>
               | <body>
               |  <h2>Forbidden</h2>
               |
               |  You don't have permission to access <code>$path</code> on this server.
               |
               |  <hr />
               |
               |  <p><i>$VERSION Server at localhost Port $port</i></p>
               | </body>
               |</html>
               |""".stripMargin,
            ContentType.TEXT_HTML
          )

          response.setEntity(entity)
        }
      }

      def serveNotFound(): Unit = {
        val file404 = handlerRoot resolve "404.html"

        if (isReadableFile(file404))
          serve(file404, HttpStatus.SC_NOT_FOUND)
        else {
          response setStatusCode HttpStatus.SC_NOT_FOUND

          val entity = new NStringEntity(
            s"""<!DOCTYPE html>
               |<html>
               | <header>
               |  <title>404 Not Found</title>
               | </header>
               | <body>
               |  <h2>Not Found</h2>
               |
               |  The requested URL <code>$path</code> was not found on this server.
               |
               |  <hr />
               |
               |  <p><i>$VERSION Server at localhost Port $port</i></p>
               | </body>
               |</html>
               |""".stripMargin,
            ContentType.TEXT_HTML
          )

          response.setEntity(entity)
        }
      }

      def serveListing(): Unit = {
        val buf = new StringBuilder
        val (dirs, files) = Files
          .list(file)
          .iterator()
          .asScala
          .toList
          .partition(Files.isDirectory(_))

        for (p <- dirs.sorted ++ files.sorted) {
          val rel = handlerRoot relativize p
          val href = rel.iterator.asScala map (s =>
            URLEncoder.encode(s.toString, "UTF-8")
          ) mkString FileSystems.getDefault.getSeparator
          val icon =
            if (Files.isDirectory(p)) "inode/directory"
            else
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
                .atZone(ZoneId.systemDefault)
              )
              .replace(".", "")
          val size =
            if (Files.isDirectory(p)) ""
            else {
              val n = Files size p

              if (n < 1024)
                n.toString
              else if (n < 1048576)
                (n / 1000.0).formatted("%.1fK")
              else if (n < 1073741824)
                (n / 1000000.0).formatted("%.1fM")
              else if (n < 1099511627776L)
                (n / 1000000000.0).formatted("%.1fG")
              else
                (n / 1000000000000.0).formatted("%.1fT")
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
             |        font-family: "Lucida Console", Monaco, monospace;
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
             |    <h2>Index of <code>/${handlerRoot relativize file}</code></h2>
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
             |    <p><i>$VERSION Server at localhost Port $port</i></p>
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
