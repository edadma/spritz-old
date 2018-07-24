package xyz.hyperreal.spritz

import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import com.sun.net.httpserver.HttpServer
import org.apache.http.ExceptionLogger
import org.apache.http.HttpConnection
import org.apache.http.HttpException
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.MethodNotSupportedException
import org.apache.http.entity.ContentType
import org.apache.http.impl.nio.bootstrap.HttpServer
import org.apache.http.impl.nio.bootstrap.ServerBootstrap
import org.apache.http.impl.nio.reactor.IOReactorConfig
import org.apache.http.nio.entity.NFileEntity
import org.apache.http.nio.entity.NStringEntity
import org.apache.http.nio.protocol.BasicAsyncRequestConsumer
import org.apache.http.nio.protocol.BasicAsyncResponseProducer
import org.apache.http.nio.protocol.HttpAsyncExchange
import org.apache.http.nio.protocol.HttpAsyncRequestConsumer
import org.apache.http.nio.protocol.HttpAsyncRequestHandler
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.HttpCoreContext
import org.apache.http.ssl.SSLContexts


object Main extends App {

    if (args.length < 1) {
      System.err.println("Please specify document root directory")
      System.exit(1)
    }
    // Document root directory
    val docRoot = new File(args(0))
    val port = 8080
    val sslContext = null
    val config =
      IOReactorConfig.custom.
        setSoTimeout(15000).
        setTcpNoDelay(true).
        build
    val server =
      ServerBootstrap.bootstrap.
        setListenerPort(port).
        setServerInfo("Test/1.1").
        setIOReactorConfig(config).
        setSslContext(sslContext).
        setExceptionLogger(ExceptionLogger.STD_ERR).
        registerHandler("*", new HttpFileHandler(docRoot)).
        create
    server.start
    System.out.println("Serving " + docRoot + " on " + server.getEndpoint.getAddress )
    server.awaitTermination(Long.MaxValue, TimeUnit.DAYS)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        server.shutdown(5, TimeUnit.SECONDS)
      }
    })
  }

  private class HttpFileHandler(val docRoot: File) extends HttpAsyncRequestHandler[HttpRequest] {
    def processRequest(request: HttpRequest, context: HttpContext): HttpAsyncRequestConsumer[HttpRequest] = { // Buffer request content in memory for simplicity
      new BasicAsyncRequestConsumer
    }

    def handle(request: HttpRequest, httpexchange: HttpAsyncExchange, context: HttpContext): Unit = {
      val response = httpexchange.getResponse
      handleInternal(request, response, context)
      httpexchange.submitResponse(new BasicAsyncResponseProducer(response))
    }

    private def handleInternal(request: HttpRequest, response: HttpResponse, context: HttpContext): Unit = {
      val method = request.getRequestLine.getMethod.toUpperCase(Locale.ENGLISH)
      if (!(method == "GET") && !(method == "HEAD") && !(method == "POST")) throw new MethodNotSupportedException(method + " method not supported")
      val target = request.getRequestLine.getUri
      val file = new File(this.docRoot, URLDecoder.decode(target, "UTF-8"))
      if (!file.exists) {
        response.setStatusCode(HttpStatus.SC_NOT_FOUND)
        val entity = new NStringEntity("<html><body><h1>File " + file.getPath + " not found</h1></body></html>", ContentType.create("text/html", "UTF-8"))
        response.setEntity(entity)
        System.out.println("File " + file.getPath + " not found")
      }
      else if (!file.canRead || file.isDirectory) {
        response.setStatusCode(HttpStatus.SC_FORBIDDEN)
        val entity = new NStringEntity("<html><body><h1>Access denied</h1></body></html>", ContentType.create("text/html", "UTF-8"))
        response.setEntity(entity)
        System.out.println("Cannot read file " + file.getPath)
      }
      else {
        val coreContext = HttpCoreContext.adapt(context)
        val conn = coreContext.getConnection(classOf[HttpConnection])
        response.setStatusCode(HttpStatus.SC_OK)
        val body = new NFileEntity(file, ContentType.create("text/html"))
        response.setEntity(body)
        System.out.println(conn + ": serving file " + file.getPath)
      }
    }

}