package org.zhutou.skeeter

import org.slf4s.Logging

trait Processor extends Logging {
  client: ChannelActor =>

  val storage: Storage = RedisStorage
  private val maxSupportedQosLevel: Byte = MessageQoSLevel.AT_LEAST_ONCE

  def process(m: MQTTMessage) = {
    m match {
      case message: MQTTConnMessage => processConnect(message)
      case message: MQTTSubscribeMessage => processSubscribe(message)
      case message: MQTTUnSubscribeMessage => processUnSubscribe(message)
      case message: MQTTPublishMessage => processPublish(message)
      case message: MQTTPingReqMessage => processPingReq()
      case message: MQTTDisconnectMessage => processDisconnect()
      case _ => disconnect()
    }
  }

  private def processDisconnect() = disconnect()

  private def processPingReq() = writeAndFlush(MQTTPingRespMessage())

  private def processPublish(message: MQTTPublishMessage) {
    message.header.mQoSLevel match {
      case MessageQoSLevel.AT_MOST_ONCE =>
      case MessageQoSLevel.AT_LEAST_ONCE =>
        val resp = MQTTPubAckMessage(message.mMessageId)
        writeAndFlush(resp)
      case MessageQoSLevel.EXACTLY_ONCE =>
        val resp = new MQTTPubRecMessage(message.mMessageId)
        writeAndFlush(resp)
    }

    //TODO retainMessage
    
    val messageId = storage.save(message)
    PubSubActor !(PubSubActor.Publish, message.mTopicName, messageId)
  }

  private def processUnSubscribe(message: MQTTUnSubscribeMessage) {
    val resp = MQTTUnSubAckMessage(message.mMessageId)
    writeAndFlush(resp)

    storage.unSubscribe(mClientId, message.mTopicNames)
    PubSubActor !(PubSubActor.UnSubscribe, mClientId, message.mTopicNames)
  }

  private def processSubscribe(message: MQTTSubscribeMessage) {
    val msg = MQTTSubAckMessage(message.mMessageId, message.mSubscriptions.map(
      s => if (s.mQoSLevel > maxSupportedQosLevel) maxSupportedQosLevel else s.mQoSLevel)
    )
    writeAndFlush(msg)

    storage.subscribe(mClientId, message.mSubscriptions)
    PubSubActor !(PubSubActor.Subscribe, mClientId, message.mSubscriptions.map(_.mTopicName))
  }

  private def processConnect(message: MQTTConnMessage) {
    val returnCode = handleConn(message)
    writeAndFlush(MQTTConnAckMessage(returnCode))
    if (returnCode == MessageConnectAckCode.ACCEPTED) {

      Container.activeChannels.get(message.mClientId) match {
        case Some(anotherClient) => anotherClient.disconnect()
        case None =>
      }
      fill(message)
      handleCleanSession()
      dispatchInFlightMessages()
      //TODO retainMessage
    } else {
      disconnect()
    }
  }

  private def handleConn(message: MQTTConnMessage): Byte = {
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

  def disconnect() = {
    log.debug("disconnect:" + mClientId);

    Container.activeChannels.remove(mClientId) match {
      case Some(c) =>
        if (mWillFlag) {
          sendWillMessage()
        }
        handleCleanSession()

      case None => log.debug("Channel has closed.")
    }

    client.ctx.close()
    exit()
  }

  def sendWillMessage() {
    val message = MQTTPublishMessage(false, mWillQosLevel, mWillRetainFlag, mWillTopic, 1, mWillMessage.getBytes("UTF-8"))
    val messageId = storage.save(message)
    PubSubActor !(PubSubActor.Publish, message.mTopicName, messageId)
  }

  private def dispatchInFlightMessages() {
    var messageId = storage.getFromInbox(mClientId)
    while (messageId != null) {
      val message0 = storage.load(messageId)
      val message = new MQTTPublishMessage(false, MessageQoSLevel.AT_MOST_ONCE, false, message0.mTopicName, 1, message0.mPayload)
      writeAndFlush(message)
      messageId = storage.getFromInbox(mClientId)
    }
  }

  private def handleCleanSession() {
    if (mCleanSessionFlag) {
      storage.flushInbox(mClientId)
      val subscribedTopicNames = storage.unSubscribe(mClientId)
      PubSubActor !(PubSubActor.UnSubscribe, mClientId, subscribedTopicNames)
    }
  }
}