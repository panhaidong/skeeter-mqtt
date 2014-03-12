package org.zhutou.skeeter

import io.netty.channel.ChannelHandlerContext
import scala.actors.Actor
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

  def act() {
    loop {
      react {
        //read
        case in: ByteBuf =>
          val message = decode(in)
          process(message)
        //write
        case message: MQTTMessage => writeAndFlush(message)
      }
    }
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
    ctx.attr(Server.ClientId).set(this)
    Container.activeChannels.getOrElseUpdate(message.mClientId, this)
  }
}