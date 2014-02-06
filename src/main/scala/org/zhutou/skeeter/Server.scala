package org.zhutou.skeeter

import scala.collection.mutable.Map
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.socket.SocketChannel

import io.netty.util.concurrent.DefaultThreadFactory
import io.netty.buffer.ByteBuf
import org.slf4s.Logging

object Server extends Logging with App {

  val clientIdMapper = Map[String, ChannelActor]()
  val contentMapper = Map[ChannelHandlerContext, ChannelActor]()

  log.info("server starting...")
  runServer
  log.info("server stopped.")


  private def newActor(ctx: ChannelHandlerContext): ChannelActor = {
    val client = new ChannelActor(ctx)
    client.start()
    client
  }

  def getClient(ctx: ChannelHandlerContext): ChannelActor = {
    contentMapper.getOrElseUpdate(ctx, newActor(ctx))
  }

  def disconnect(ctx: ChannelHandlerContext) = {
    contentMapper.remove(ctx) match {
      case Some(client: ChannelActor) => clientIdMapper.remove(client.mClientId)
      case None =>
    }
    ctx.close
  }

  def runServer {
    ProcessActor.start

    val bossGroup: EventLoopGroup = new NioEventLoopGroup(5, new DefaultThreadFactory("BOSS"))
    val workerGroup: EventLoopGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("WORKER"))

    try {
      val b = new ServerBootstrap
      b.group(bossGroup, workerGroup)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new ChannelInitializer[SocketChannel]() {
        override def initChannel(ch: SocketChannel) {
          ch.pipeline.addLast(
            new ChannelInboundHandlerAdapter() {
              override def channelRead(ctx: ChannelHandlerContext, msg: Object) {
                log.debug("channelRead..." + ctx)
                val client = getClient(ctx)
                client ! msg.asInstanceOf[ByteBuf]
                log.debug("channelRead end")
              }

              override def channelInactive(ctx: ChannelHandlerContext) {
                disconnect(ctx)
              }

              override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                cause.printStackTrace
                ctx.close
              }
            }
          )
        }
      })
        .option(ChannelOption.SO_BACKLOG, 128: Integer)
        .childOption(ChannelOption.SO_KEEPALIVE, true: java.lang.Boolean)

      val f = b.bind(Config.serverPort).sync
      f.channel().closeFuture.sync
    } finally {
      workerGroup.shutdownGracefully
      bossGroup.shutdownGracefully
    }
  }
}
