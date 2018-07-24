package xyz.hyperreal.spritz

import java.nio.file.{Files, Path}
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

import org.apache.http.{ExceptionLogger, HttpConnection, HttpRequest, HttpResponse, HttpStatus,
  MethodNotSupportedException}
import org.apache.http.entity.ContentType
import org.apache.http.impl.nio.bootstrap.ServerBootstrap
import org.apache.http.impl.nio.reactor.IOReactorConfig
import org.apache.http.nio.entity.{NFileEntity, NStringEntity}
import org.apache.http.nio.protocol.{BasicAsyncRequestConsumer, BasicAsyncResponseProducer, HttpAsyncExchange,
  HttpAsyncRequestConsumer, HttpAsyncRequestHandler}
import org.apache.http.protocol.{HttpContext, HttpCoreContext}


class Server( val docRoot: Path, val port: Int ) {

  require( Files.exists(docRoot) && Files.isDirectory(docRoot) && Files.isReadable(docRoot),
    s"Document root must be an accessible directory" )

  val rootdir = docRoot.toAbsolutePath.normalize
  val sslContext = null
  val config =
    IOReactorConfig.custom.
      setSoTimeout(15000).
      setTcpNoDelay(true).
      build
  val server =
    ServerBootstrap.bootstrap.
      setListenerPort(port).
      setServerInfo("Spritz/0.1").
      setIOReactorConfig(config).
      setSslContext(sslContext).
      setExceptionLogger(ExceptionLogger.STD_ERR).
      registerHandler("*", new HttpFileHandler(rootdir)).
      create

  def start = {
    server.start
    println( s"Serving $rootdir on ${server.getEndpoint.getAddress}" )

    Runtime.getRuntime.addShutdownHook( new Thread {
      override def run: Unit = {
        shutdown
      }
    } )
  }

  def shutdown = server.shutdown( 5, TimeUnit.SECONDS )

  def await = server.awaitTermination( Long.MaxValue, TimeUnit.DAYS )

  private class HttpFileHandler( val rootdir: Path ) extends HttpAsyncRequestHandler[HttpRequest] {
    def processRequest( request: HttpRequest, context: HttpContext ): HttpAsyncRequestConsumer[HttpRequest] = {
      new BasicAsyncRequestConsumer
    }

    def handle( request: HttpRequest, httpexchange: HttpAsyncExchange, context: HttpContext ): Unit = {
      val response = httpexchange.getResponse

      handleInternal( request, response, context )
      httpexchange.submitResponse( new BasicAsyncResponseProducer(response) )
    }

    private def handleInternal( request: HttpRequest, response: HttpResponse, context: HttpContext ): Unit = {
      val method = request.getRequestLine.getMethod.toUpperCase

      if (!(method == "GET") && !(method == "HEAD") && !(method == "POST")) throw new MethodNotSupportedException(method + " method not supported")

      val target = request.getRequestLine.getUri
      val file = rootdir resolve (URLDecoder.decode( target, "UTF-8" ) drop 1)

      if (!Files.exists( file )) {
        response setStatusCode HttpStatus.SC_NOT_FOUND

        val entity = new NStringEntity(s"<html><body><h1>File $file not found</h1></body></html>", ContentType.create("text/html", "UTF-8"))

        response.setEntity(entity)
        println( s"File $file not found")
      } else if (!Files.isReadable(file) || Files.isDirectory( file )) {
        response.setStatusCode(HttpStatus.SC_FORBIDDEN)
        val entity = new NStringEntity("<html><body><h1>Access denied</h1></body></html>", ContentType.create("text/html", "UTF-8"))
        response.setEntity(entity)
        System.out.println( s"Cannot read file :$file" )
      } else {
        val coreContext = HttpCoreContext.adapt(context)
        val conn = coreContext.getConnection(classOf[HttpConnection])
        response.setStatusCode(HttpStatus.SC_OK)
        val body = new NFileEntity(file.toFile, ContentType.create("text/html"))
        response.setEntity(body)
        System.out.println( s"$conn: serving file $file" )
      }
    }
  }

}