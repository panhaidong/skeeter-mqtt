package org.zhutou.skeeter

import scala.actors.Actor
import org.slf4s.Logging

object ProcessActor extends Actor with Logging {
  val pubsub: PubSub = RedisPubSub

  override def start = {
    pubsub.startDispatch
    super.start
  }

  def act() {
    loop {
      react {
        case (client: ChannelActor, message: MQTTMessage) =>
          try {
            process(client, message) match {
              case Some(msg: MQTTMessage) =>
                log.debug("ProcessActor send:" + msg);
                client ! msg
              case _ =>
            }
          } catch {
            case ex: UnSupportedRequestMessageException => client.disconnect
            case _: Throwable =>
          }
        case message: MQTTPublishMessage =>
          pubsub.publish(message.mTopicName, message.mPayload.array)
        case _ =>
      }
    }
  }

  def process(client: ChannelActor, m: MQTTMessage): Option[MQTTMessage] = {
    log.debug("process " + m);

    m match {
      case message: MQTTConnMessage =>
        val header = new MessageHeader(MessageType.CONNACK, false, MessageQoSLevel.AT_MOST_ONCE, false, 0)
        if (message.mProtocolName != "MQIsdp") {
          Some(new MQTTConnAckMessage(header, MessageConnectAckCode.UNACCEPTABLE_PROTOCOL_VERSION))
        } else if (message.mProtocolVersion != 3) {
          Some(new MQTTConnAckMessage(header, MessageConnectAckCode.UNACCEPTABLE_PROTOCOL_VERSION))
        } else if (message.mClientId.size > 22) {
          Some(new MQTTConnAckMessage(header, MessageConnectAckCode.IDENTIFIER_REJECTED))
        } else {
          client.fill(message)
          Server.clientIdMapper.getOrElseUpdate(message.mClientId, client)
          Some(new MQTTConnAckMessage(header, MessageConnectAckCode.ACCEPTED))
        }

      case message: MQTTSubscribeMessage =>
        pubsub.subscribe(client.mClientId, message.mSubscriptions.map(_._1))
        val header = new MessageHeader(MessageType.SUBACK, false, MessageQoSLevel.AT_MOST_ONCE, false, 0)
        Some(new MQTTSubAckMessage(header, message.mMessageId, List(MessageQoSLevel.AT_MOST_ONCE)))

      case message: MQTTUnSubscribeMessage =>
        pubsub.unSubscribe(client.mClientId, message.mTopicNames)
        val header = new MessageHeader(MessageType.UNSUBACK, false, MessageQoSLevel.AT_MOST_ONCE, false, 0)
        Some(new MQTTSubAckMessage(header, message.mMessageId, List(MessageQoSLevel.AT_MOST_ONCE)))

      case message: MQTTPublishMessage =>
        pubsub.publish(message.mTopicName, message.mPayload.array)
        message.header.mQoSLevel match {
          case MessageQoSLevel.AT_MOST_ONCE =>
            None
          case MessageQoSLevel.AT_LEAST_ONCE =>
            val header = new MessageHeader(MessageType.PUBACK, false, MessageQoSLevel.AT_MOST_ONCE, false, 0)
            Some(new MQTTPubAckMessage(header, message.mMessageId))
          case MessageQoSLevel.EXACTLY_ONCE =>
            val header = new MessageHeader(MessageType.PUBREC, false, MessageQoSLevel.AT_MOST_ONCE, false, 0)
            Some(new MQTTPubRecMessage(header, message.mMessageId))
        }

      case message: MQTTPingReqMessage =>
        val header = new MessageHeader(MessageType.PINGRESP, false, MessageQoSLevel.AT_MOST_ONCE, false, 0)
        Some(new MQTTPingRespMessage(header))

      case message: MQTTDisconnectMessage =>
        None
      case _ =>
        throw new UnSupportedRequestMessageException
    }
  }

}