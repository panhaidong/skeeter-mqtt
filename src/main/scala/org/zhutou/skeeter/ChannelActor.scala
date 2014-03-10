package org.zhutou.skeeter

import io.netty.channel.ChannelHandlerContext
import scala.actors.Actor
import io.netty.buffer.ByteBuf
import org.slf4s.Logging
import java.nio.ByteBuffer
import org.zhutou.skeeter.ChannelActor.Stop

object ChannelActor{
  case object Stop
}
class ChannelActor(ctx: ChannelHandlerContext) extends Actor with Logging {
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
          ProcessActor !(this, message)
        //write
        case message: MQTTMessage => writeAndFlush(message)
        //stop
        case Stop => disconnect
      }
    }
  }

  def decode(in: ByteBuf): MQTTMessage = {
    log.debug("decode start...")
    val message = MQTTUtils.decode(in)
    log.debug("decode:" + message)
    message
  }

  def writeAndFlush(msg: MQTTMessage) = {
    log.debug("encode:" + msg)
    ctx.writeAndFlush(MQTTUtils.encode(ctx, msg))
  }

  def disconnect = {
    log.debug("disconnect:" + mClientId);
    if (this.mWillFlag) {
      val message = new MQTTPublishMessage(false, mWillQosLevel, mWillRetainFlag, mWillTopic, 1, mWillMessage.getBytes("UTF-8"))
      ProcessActor ! message
    }
    if(this.mCleanSessionFlag){
      
    }

    Container.activeChannels.remove(mClientId)
    ctx.close()
    exit()
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


