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
import io.netty.util.AttributeKey

object Container {
  val activeChannels = Map[String, ChannelActor]()
}

object Server extends Logging with App {


  log.info("server starting...")
  runServer
  log.info("server stopped.")
  val ClientId: AttributeKey[ChannelActor] = AttributeKey.valueOf("SKEETER_CHANNEL_ACTOR")

  private def newActor(ctx: ChannelHandlerContext): ChannelActor = {
    val client = new ChannelActor(ctx)
    client.start()
    client
  }

  def runServer {
    ProcessActor.start
    PubSubActor.start

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
                log.debug("channelRead..." + ctx + ", msg=" + msg)
                var channel = ctx.attr(ClientId).get()
                if (channel == null)
                  channel = newActor(ctx)
                channel ! msg.asInstanceOf[ByteBuf]
                log.debug("channelRead end")
              }

              override def channelInactive(ctx: ChannelHandlerContext) = {
                var channel = ctx.attr(ClientId).get()
                if (channel == null)
                  ctx.close()
                else
                  channel.disconnect

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
