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
import akka.actor.{ActorRef, ActorDSL, ActorSystem}

object Container {
  val activeChannels = Map[String, ActorRef]()
}

object Server extends Logging with App {


  log.info("server starting...")
  val system = ActorSystem("skeeter")
  val pubSubActor = ActorDSL.actor(system)(new PubSubActor)
  runServer()
  log.info("server stopped.")
  val ClientId: AttributeKey[ActorRef] = AttributeKey.valueOf("SKEETER_CHANNEL_ACTOR")

  private def newActor(ctx: ChannelHandlerContext) = ActorDSL.actor(system)(new ChannelActor(ctx))

  def runServer() {

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
                var channel = ctx.attr(ClientId).get()
                log.debug("channelRead...channel=" + channel)
                if (channel == null)
                  channel = newActor(ctx)
                channel ! msg.asInstanceOf[ByteBuf]
                log.debug("channelRead end")
              }

              override def channelInactive(ctx: ChannelHandlerContext) = {

              }

              override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                ctx.attr(ClientId).get() match {
                  case null => ctx.close()
                  case channel: ActorRef => channel ! "quit"
                }
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
