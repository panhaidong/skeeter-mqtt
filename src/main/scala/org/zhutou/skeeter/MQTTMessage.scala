package org.zhutou.skeeter


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

case class MessageSubscription(mClientId: String, mTopicName: String, mQoSLevel: Byte)

abstract class MQTTMessage extends Serializable {
  val header: MessageHeader
}

case class MQTTConnMessage(mProtocolName: String, mProtocolVersion: Byte, mCleanSessionFlag: Boolean,
                           mWillFlag: Boolean, mWillQosLevel: Byte, mWillRetainFlag: Boolean, mPasswordFlag: Boolean,
                           mUserNameFlag: Boolean, mKeepAliveTimer: Int, mClientId: String, mWillTopic: String,
                           mWillMessage: String, mUserName: String, mPassword: String) extends MQTTMessage {
  val header = new MessageHeader(MessageType.CONNECT, false, 0: Byte, false, 0: Int)
}

case class MQTTConnAckMessage(mReturnCode: Byte) extends MQTTMessage {
  val header = new MessageHeader(MessageType.CONNACK, false, 0: Byte, false, 0: Int)
}

case class MQTTPublishMessage(mDupFlag: Boolean, mQoSLevel: Byte, mRetainFlag: Boolean, mTopicName: String,
                              mMessageId: Int, mPayload: Array[Byte]) extends MQTTMessage {
  val header = new MessageHeader(MessageType.PUBLISH, mDupFlag, mQoSLevel, mRetainFlag, 0)
}

case class MQTTPubAckMessage(mMessageId: Int) extends MQTTMessage {
  val header = new MessageHeader(MessageType.PUBACK, false, 0: Byte, false, 0)
}

case class MQTTPubRecMessage(mMessageId: Int) extends MQTTMessage {
  val header = new MessageHeader(MessageType.PUBREC, false, 0: Byte, false, 0)
}

case class MQTTPubRelMessage(mMessageId: Int) extends MQTTMessage {
  val header = new MessageHeader(MessageType.PUBREL, false, 0: Byte, false, 0)
}

case class MQTTPubCompMessage(mMessageId: Int) extends MQTTMessage {
  val header = new MessageHeader(MessageType.PUBCOMP, false, 0: Byte, false, 0)
}

case class MQTTSubscribeMessage(mDupFlag: Boolean, mMessageId: Int,
                                mSubscriptions: List[MessageSubscription]) extends MQTTMessage {
  val header = new MessageHeader(MessageType.SUBSCRIBE, mDupFlag, MessageQoSLevel.AT_LEAST_ONCE, false, 0)
}

case class MQTTSubAckMessage(mMessageId: Int, mQosLevels: List[Byte]) extends MQTTMessage {
  val header = new MessageHeader(MessageType.SUBACK, false, 0: Byte, false, 0)
}

case class MQTTUnSubscribeMessage(mDupFlag: Boolean, mMessageId: Int, mTopicNames: List[String]) extends MQTTMessage {
  val header = new MessageHeader(MessageType.UNSUBSCRIBE, mDupFlag, MessageQoSLevel.AT_LEAST_ONCE, false, 0)
}

case class MQTTUnSubAckMessage(mMessageId: Int) extends MQTTMessage {
  val header = new MessageHeader(MessageType.UNSUBACK, false, 0: Byte, false, 0)
}

case class MQTTPingReqMessage() extends MQTTMessage {
  val header = new MessageHeader(MessageType.PINGREQ, false, 0: Byte, false, 0)
}

case class MQTTPingRespMessage() extends MQTTMessage {
  val header = new MessageHeader(MessageType.PINGRESP, false, 0: Byte, false, 0)
}

case class MQTTDisconnectMessage() extends MQTTMessage {
  val header = new MessageHeader(MessageType.DISCONNECT, false, 0: Byte, false, 0)
}

class UnSupportedRequestMessageException extends Exception

class UnSupportedResponseMessageException extends Exception