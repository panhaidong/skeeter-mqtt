package org.zhutou.skeeter

import io.netty.channel.ChannelHandlerContext
import scala.actors.Actor
import io.netty.buffer.ByteBuf
import org.slf4s.Logging
import java.nio.ByteBuffer


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
        case message: MQTTMessage =>
          val buf = encode(message)
          ctx.writeAndFlush(buf)
      }
    }
  }

  def decode(in: ByteBuf): MQTTMessage = {
    log.debug("decode start...");
    val message = MQTTUtils.decode(in)
    log.debug("decode:" + message);
    message
  }

  def encode(msg: MQTTMessage): ByteBuf = {
    log.debug("encode:" + msg);
    MQTTUtils.encode(ctx, msg)
  }

  def disconnect = {
    if (this.mWillFlag) {
      val header = new MessageHeader(MessageType.PUBLISH, false, mWillQosLevel, false, 0)
      val message = new MQTTPublishMessage(header, mWillTopic, 1, ByteBuffer.wrap(mWillMessage.getBytes("UTF-8")))
      ProcessActor ! message
    }
    Server.disconnect(ctx)
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
  }
}



