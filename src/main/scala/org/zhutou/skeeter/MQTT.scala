package org.zhutou.skeeter

import java.nio.ByteBuffer

object MessageType {
  val CONNECT: Byte = 1
  val CONNACK: Byte = 2
  val PUBLISH: Byte = 3
  val PUBACK: Byte = 4
  val PUBREC: Byte = 5
  val PUBREL: Byte = 6
  val PUBCOMP: Byte = 7
  val SUBSCRIBE: Byte = 8
  val SUBACK: Byte = 9
  val UNSUBSCRIBE: Byte = 10
  val UNSUBACK: Byte = 11
  val PINGREQ: Byte = 12
  val PINGRESP: Byte = 13
  val DISCONNECT: Byte = 14
}

object MessageQoSLevel {
  val AT_MOST_ONCE: Byte = 0
  val AT_LEAST_ONCE: Byte = 1
  val EXACTLY_ONCE: Byte = 2
}

object MessageConnectAckCode {
  val ACCEPTED: Byte = 0
  val UNACCEPTABLE_PROTOCOL_VERSION: Byte = 1
  val IDENTIFIER_REJECTED: Byte = 2
  val SERVER_UNAVAILABLE: Byte = 3
  val BAD_USERNAME_OR_PASSWORD: Byte = 4
  val NOT_AUTHORIZED: Byte = 5
}

case class MessageHeader(mType: Byte, mDupFlag: Boolean, mQoSLevel: Byte, mRetainFlag: Boolean, mRemainingLength: Int)

abstract class MQTTMessage extends Serializable {
  val header: MessageHeader
}

case class MQTTConnMessage(header: MessageHeader, mProtocolName: String, mProtocolVersion: Byte,
                           mCleanSessionFlag: Boolean, mWillFlag: Boolean, mWillQosLevel: Byte, mWillRetainFlag: Boolean,
                           mPasswordFlag: Boolean, mUserNameFlag: Boolean, mKeepAliveTimer: Int, mClientId: String,
                           mWillTopic: String, mWillMessage: String, mUserName: String, mPassword: String) extends MQTTMessage

case class MQTTConnAckMessage(header: MessageHeader, mReturnCode: Byte) extends MQTTMessage

case class MQTTPublishMessage(header: MessageHeader, mTopicName: String, mMessageId: Int, mPayload: ByteBuffer) extends MQTTMessage

case class MQTTPubAckMessage(header: MessageHeader, mMessageId: Int) extends MQTTMessage

case class MQTTPubRecMessage(header: MessageHeader, mMessageId: Int) extends MQTTMessage

case class MQTTPubRelMessage(header: MessageHeader, mMessageId: Int) extends MQTTMessage

case class MQTTPubCompMessage(header: MessageHeader, mMessageId: Int) extends MQTTMessage

case class MQTTSubscribeMessage(header: MessageHeader, mMessageId: Int, mSubscriptions: List[(String, Byte)]) extends MQTTMessage

case class MQTTSubAckMessage(header: MessageHeader, mMessageId: Int, mQosLevels: List[Byte]) extends MQTTMessage

case class MQTTUnSubscribeMessage(header: MessageHeader, mMessageId: Int, mTopicNames: List[String]) extends MQTTMessage

case class MQTTUnSubAckMessage(header: MessageHeader, mMessageId: Int) extends MQTTMessage

case class MQTTPingReqMessage(header: MessageHeader) extends MQTTMessage

case class MQTTPingRespMessage(header: MessageHeader) extends MQTTMessage

case class MQTTDisconnectMessage(header: MessageHeader) extends MQTTMessage

class UnSupportedRequestMessageException extends Exception

class UnSupportedResponseMessageException extends Exception