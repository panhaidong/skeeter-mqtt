package org.zhutou.skeeter

import io.netty.buffer.{Unpooled, ByteBuf}
import io.netty.handler.codec.CorruptedFrameException
import io.netty.channel.ChannelHandlerContext
import org.slf4s.Logging

object MQTTUtils extends Logging {
  def readByte(in: ByteBuf): Byte = in.readByte()

  def readInt(in: ByteBuf): Int =  in.readUnsignedShort()


  def readString(in: ByteBuf): String = {
    if (in.readableBytes < 2) {
      return null
    }
    val strLen: Int = in.readUnsignedShort
    if (in.readableBytes < strLen) {
      return null
    }
    val strRaw: Array[Byte] = new Array[Byte](strLen)
    in.readBytes(strRaw)
    return new String(strRaw, "UTF-8")
  }

  def writeInt(out: ByteBuf, int: Int): ByteBuf = out.writeShort(int)

  def writeString(out: ByteBuf, str: String) = {
    var raw: Array[Byte] = null
    raw = str.getBytes("UTF-8")
    out.writeShort(raw.length)
    out.writeBytes(raw)
  }

  def decodeRemainingLength(in: ByteBuf): Int = {
    var multiplier: Int = 1
    var value: Int = 0
    var digit: Byte = 0
    do {
      if (in.readableBytes < 1) {
        return -1
      }
      digit = in.readByte
      value += (digit & 0x7F) * multiplier
      multiplier *= 128
    } while ((digit & 0x80) != 0)
    return value
  }

  def encodeRemainingLength(value0: Int): ByteBuf = {
    val MAX_LENGTH_LIMIT: Int = 268435455
    if (value0 > MAX_LENGTH_LIMIT || value0 < 0) {
      throw new CorruptedFrameException("Value should in range 0.." + MAX_LENGTH_LIMIT + " found " + value0)
    }
    val encoded: ByteBuf = Unpooled.buffer(4)
    var digit: Byte = 0
    var value = value0
    do {
      digit = (value % 128).asInstanceOf[Byte]
      value = value / 128
      if (value > 0) {
        digit = (digit | 0x80).asInstanceOf[Byte]
      }
      encoded.writeByte(digit)
    } while (value > 0)
    return encoded
  }

  private def decodeMessageHeader(in: ByteBuf): MessageHeader = {
    if (!checkHeaderAvailability(in)) {
      return null
    }
    val b1 = in.readByte()
    val mType = ((b1 & 0x00F0) >> 4).asInstanceOf[Byte]
    val mDupFlag = ((b1 & 0x0008) >> 3) == 1
    val mQoSLevel = ((b1 & 0x0006) >> 1).asInstanceOf[Byte]
    val mRetainFlag = (b1 & 0x0001) == 1
    val mRemainingLength = MQTTUtils.decodeRemainingLength(in)
    MessageHeader(mType, mDupFlag, mQoSLevel, mRetainFlag, mRemainingLength)
  }


  def decode(in: ByteBuf, clientId: String): MQTTMessage = {
    if (!checkHeaderAvailability(in)) {
      log.debug("readableBytes=" + in.readableBytes())
      return null
    }
    val header = decodeMessageHeader(in)
    val bytes = in.readBytes(header.mRemainingLength)

    header.mType match {
      case MessageType.CONNECT =>
        val mProtocolName = MQTTUtils.readString(bytes)
        val mProtocolVersion = MQTTUtils.readByte(bytes)
        val connectFlags = MQTTUtils.readByte(bytes)
        val mCleanSessionFlag = ((connectFlags & 0x02) >> 1) == 1
        val mWillFlag = ((connectFlags & 0x04) >> 2) == 1
        val mWillQosLevel = ((connectFlags & 0x18) >> 3).asInstanceOf[Byte]
        val mWillRetainFlag = ((connectFlags & 0x20) >> 5) == 1
        val mPasswordFlag = ((connectFlags & 0x40) >> 6) == 1
        val mUserNameFlag = ((connectFlags & 0x80) >> 7) == 1
        val mKeepAliveTimer = MQTTUtils.readInt(bytes)

        val mClientId = MQTTUtils.readString(bytes)
        val mWillTopic = if (mWillFlag) MQTTUtils.readString(bytes) else ""
        val mWillMessage = if (mWillFlag) MQTTUtils.readString(bytes) else ""
        val mUserName = if (mUserNameFlag) MQTTUtils.readString(bytes) else ""
        val mPassword = if (mPasswordFlag) MQTTUtils.readString(bytes) else ""

        MQTTConnMessage(mProtocolName, mProtocolVersion, mCleanSessionFlag, mWillFlag,
          mWillQosLevel, mWillRetainFlag, mPasswordFlag, mUserNameFlag, mKeepAliveTimer, mClientId, mWillTopic,
          mWillMessage, mUserName, mPassword)

      case MessageType.CONNACK =>
        bytes.skipBytes(1)
        val mReturnCode = bytes.readByte()
        MQTTConnAckMessage(mReturnCode)

      case MessageType.PUBLISH =>
        val mTopicName = MQTTUtils.readString(bytes)
        val mMessageId = if (header.mQoSLevel == MessageQoSLevel.AT_LEAST_ONCE
          || header.mQoSLevel == MessageQoSLevel.EXACTLY_ONCE) MQTTUtils.readInt(bytes)
        else 0
        val mPayload = bytes.readBytes(bytes.readableBytes())
        MQTTPublishMessage(header.mDupFlag, header.mQoSLevel, header.mRetainFlag, mTopicName, mMessageId, mPayload.array)

      case MessageType.PUBACK =>
        val mMessageId = MQTTUtils.readInt(bytes)
        MQTTPubAckMessage(mMessageId)

      case MessageType.PUBREC =>
        val mMessageId = MQTTUtils.readInt(bytes)
        MQTTPubRecMessage(mMessageId)

      case MessageType.PUBREL =>
        val mMessageId = MQTTUtils.readInt(bytes)
        MQTTPubRelMessage(mMessageId)

      case MessageType.PUBCOMP =>
        val mMessageId = MQTTUtils.readInt(bytes)
        MQTTPubCompMessage(mMessageId)

      case MessageType.SUBSCRIBE =>
        val mMessageId = MQTTUtils.readInt(bytes)

        var subscriptions = List[MessageSubscription]()
        while (bytes.readableBytes() > 0) {
          val topicName = MQTTUtils.readString(bytes)
          val qoSLevel = MQTTUtils.readByte(bytes)
          subscriptions = MessageSubscription(clientId, topicName, qoSLevel) :: subscriptions
        }
        val mSubscriptions = subscriptions.reverse
        MQTTSubscribeMessage(header.mDupFlag, mMessageId, mSubscriptions)

      case MessageType.SUBACK =>
        val mMessageId = MQTTUtils.readInt(bytes)

        var mQosLevels = List[Byte]()
        while (bytes.readableBytes() > 0) {
          val qoSLevel = MQTTUtils.readByte(bytes)
          mQosLevels = qoSLevel :: mQosLevels
        }
        mQosLevels = mQosLevels.reverse

        MQTTSubAckMessage(mMessageId, mQosLevels)

      case MessageType.UNSUBSCRIBE =>
        val mMessageId = MQTTUtils.readInt(bytes)

        var mTopicNames = List[String]()
        while (bytes.readableBytes() > 0) {
          val topicName = MQTTUtils.readString(bytes)
          mTopicNames = topicName :: mTopicNames
        }
        mTopicNames = mTopicNames.reverse

        MQTTUnSubscribeMessage(header.mDupFlag, mMessageId, mTopicNames)

      case MessageType.UNSUBACK =>
        val mMessageId = MQTTUtils.readInt(bytes)
        MQTTUnSubAckMessage(mMessageId)

      case MessageType.PINGREQ =>
        MQTTPingReqMessage()

      case MessageType.PINGRESP =>
        MQTTPingRespMessage()

      case MessageType.DISCONNECT =>
        MQTTDisconnectMessage()

      case _ =>
        log.error("Wrong message")
        return null
    }

  }

  def encode(ctx: ChannelHandlerContext, msg: MQTTMessage): ByteBuf = {
    val header = msg.header
    //FixedHeader
    var b0 = header.mType << 4
    if (header.mDupFlag)
      b0 |= 0x08
    if (header.mRetainFlag)
      b0 |= 0x01
    b0 |= header.mQoSLevel << 1

    val variableHeaderBuff: ByteBuf = ctx.alloc.buffer()
    msg match {
      case m: MQTTConnMessage =>
        throw new UnSupportedResponseMessageException
      case m: MQTTConnAckMessage =>
        variableHeaderBuff.writeByte(0).writeByte(m.mReturnCode)

      case m: MQTTPublishMessage =>
        MQTTUtils.writeString(variableHeaderBuff, m.mTopicName)
        if (header.mQoSLevel == MessageQoSLevel.AT_LEAST_ONCE
          || header.mQoSLevel == MessageQoSLevel.EXACTLY_ONCE)
          MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
        variableHeaderBuff.writeBytes(m.mPayload)

      case m: MQTTPubAckMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)

      case m: MQTTPubRecMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
      case m: MQTTPubRelMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
      case m: MQTTPubCompMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
      case m: MQTTSubscribeMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
        for (s <- m.mSubscriptions) {
          MQTTUtils.writeString(variableHeaderBuff, s.mTopicName)
          variableHeaderBuff.writeByte(s.mQoSLevel)
        }
      case m: MQTTSubAckMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
        for (qoSLevel <- m.mQosLevels) {
          variableHeaderBuff.writeByte(qoSLevel)
        }
      case m: MQTTUnSubscribeMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
        for (topicName <- m.mTopicNames) {
          MQTTUtils.writeString(variableHeaderBuff, topicName)
        }
      case m: MQTTUnSubAckMessage =>
        MQTTUtils.writeInt(variableHeaderBuff, m.mMessageId)
      case m: MQTTPingReqMessage =>
      case m: MQTTPingRespMessage =>
      case m: MQTTDisconnectMessage =>
    }
    val variableHeaderSize: Int = variableHeaderBuff.readableBytes
    val buff: ByteBuf = ctx.alloc.buffer(2 + variableHeaderSize)
    buff.writeByte(b0)
    buff.writeBytes(MQTTUtils.encodeRemainingLength(variableHeaderSize))
    buff.writeBytes(variableHeaderBuff)
    buff
  }

  private def checkHeaderAvailability(in: ByteBuf): Boolean = {
    if (in.readableBytes < 1) {
      return false
    }
    in.skipBytes(1)
    val remainingLength: Int = decodeRemainingLength(in)
    in.resetReaderIndex()

    if (remainingLength == -1 || in.readableBytes < remainingLength) {
      return false
    } else {
      return true
    }
  }
}
