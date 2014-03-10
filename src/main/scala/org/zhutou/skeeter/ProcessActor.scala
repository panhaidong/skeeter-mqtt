package org.zhutou.skeeter

import scala.actors.Actor
import org.slf4s.Logging

object ProcessActor extends Actor with Logging {
  val storage: Storage = RedisStorage
  private val maxSupportedQosLevel: Byte = MessageQoSLevel.AT_LEAST_ONCE

  def act() {
    loop {
      react {
        case (client: ChannelActor, message: MQTTMessage) =>
          try {
            val m = process(client, message)
            log.debug("ProcessActor processed:" + m + ", mClientId=" + client.mClientId);
            m match {
              case Some(msg: MQTTConnAckMessage) =>
                client ! msg
                if (msg.mReturnCode != MessageConnectAckCode.ACCEPTED) {
                  client ! ChannelActor.Stop
                }
              case Some(msg: MQTTDisconnectMessage) => client ! ChannelActor.Stop
              case Some(msg: MQTTMessage) => client ! msg
              case _ =>
            }
          } catch {
            case ex: UnSupportedRequestMessageException => client ! ChannelActor.Stop
            case _: Throwable =>
          }
        //will message
        case message: MQTTPublishMessage =>
          PubSubActor !(PubSubActor.Publish, message.mTopicName, message.mPayload.array)
        case _ =>
      }
    }
  }

  def process(client: ChannelActor, m: MQTTMessage): Option[MQTTMessage] = {
    log.debug("process " + m);
    m match {
      case message: MQTTSubscribeMessage =>
        PubSubActor !(PubSubActor.Subscribe, client.mClientId, message.mSubscriptions.map(_._1))
      case message: MQTTUnSubscribeMessage =>
        PubSubActor !(PubSubActor.UnSubscribe, client.mClientId, message.mTopicNames)
      case message: MQTTPublishMessage =>
        PubSubActor !(PubSubActor.Publish, message.mTopicName, message)
      case _ =>
    }
    processResp(client, m)
  }

  def processResp(client: ChannelActor, m: MQTTMessage): Option[MQTTMessage] = {
    m match {
      case message: MQTTConnMessage =>
        val returnCode = handle(message)
        if (returnCode == MessageConnectAckCode.ACCEPTED) {
          //TODO check duplicate clientId
          client.fill(message)
          dispatchInFlightMessages(client)
        }
        Some(new MQTTConnAckMessage(returnCode))

      case message: MQTTSubscribeMessage =>
        Some(new MQTTSubAckMessage(message.mMessageId, message.mSubscriptions.map(
          s => if (s._2 > maxSupportedQosLevel) maxSupportedQosLevel else s._2)
        ))

      case message: MQTTUnSubscribeMessage =>
        Some(new MQTTUnSubAckMessage(message.mMessageId))

      case message: MQTTPublishMessage =>
        message.header.mQoSLevel match {
          case MessageQoSLevel.AT_MOST_ONCE =>
            None
          case MessageQoSLevel.AT_LEAST_ONCE =>
            Some(new MQTTPubAckMessage(message.mMessageId))
          case MessageQoSLevel.EXACTLY_ONCE =>
            Some(new MQTTPubRecMessage(message.mMessageId))
        }

      case message: MQTTPingReqMessage =>
        Some(new MQTTPingRespMessage)

      case message: MQTTDisconnectMessage =>
        Some(message)

      case _ =>
        throw new UnSupportedRequestMessageException
    }
  }

  private def handle(message: MQTTConnMessage): Byte = {
    if (message.mProtocolName != "MQIsdp") {
      MessageConnectAckCode.UNACCEPTABLE_PROTOCOL_VERSION
    } else if (message.mProtocolVersion != 3) {
      MessageConnectAckCode.UNACCEPTABLE_PROTOCOL_VERSION
    } else if (message.mClientId.size > 23) {
      MessageConnectAckCode.IDENTIFIER_REJECTED
    } else {
      MessageConnectAckCode.ACCEPTED
    }
  }

  private def dispatchInFlightMessages(client: ChannelActor) {
    var messageId = storage.getFromInbox(client.mClientId)
    println("inFlight:" + messageId)
    while (messageId != null) {
      val message0 = storage.load(messageId)
      println("inFlight:" + new String(message0.mPayload, "UTF-8"))
      val message = new MQTTPublishMessage(false, MessageQoSLevel.AT_MOST_ONCE, false, message0.mTopicName, 1, message0.mPayload)
      client ! message

      messageId = storage.getFromInbox(client.mClientId)
      println("inFlight:" + messageId)
    }
  }
}