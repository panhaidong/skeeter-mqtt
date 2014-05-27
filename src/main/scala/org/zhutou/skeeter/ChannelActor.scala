package org.zhutou.skeeter

import io.netty.channel.ChannelHandlerContext
import akka.actor.Actor
import io.netty.buffer.ByteBuf
import org.slf4s.Logging

class ChannelActor(val ctx: ChannelHandlerContext) extends Actor with Logging with Processor {
  var mClientId: String = _
  var mCleanSessionFlag: Boolean = _
  var mWillFlag: Boolean = _
  var mWillQosLevel: Byte = _
  var mWillRetainFlag: Boolean = _
  var mKeepAliveTimer: Int = _
  var mWillTopic: String = _
  var mWillMessage: String = _
  var mLastActiveTimeMillis: Long = _

  def receive = {
    //read
    case in: ByteBuf =>
      mLastActiveTimeMillis = System.currentTimeMillis
      val message = decode(in)
      process(message)
    //write
    case message: MQTTMessage => writeAndFlush(message)
  }

  def decode(in: ByteBuf): MQTTMessage = {
    log.debug(mClientId + " decode start...")
    val message = MQTTUtils.decode(in, mClientId)
    log.debug("decode:" + message)
    message
  }

  def writeAndFlush(msg: MQTTMessage) = {
    log.debug("encode:" + msg)
    ctx.writeAndFlush(MQTTUtils.encode(ctx, msg))
  }

  def fill(message: MQTTConnMessage) = {
    mClientId = message.mClientId
    mCleanSessionFlag = message.mCleanSessionFlag
    mWillFlag = message.mWillFlag
    mWillQosLevel = message.mWillQosLevel
    mWillRetainFlag = message.mWillRetainFlag
    mKeepAliveTimer = message.mKeepAliveTimer
    mWillTopic = message.mWillTopic
    mWillMessage = message.mWillMessage
    ctx.attr(Server.ClientId).set(self)
    Container.activeChannels.getOrElseUpdate(message.mClientId, self)
  }
}