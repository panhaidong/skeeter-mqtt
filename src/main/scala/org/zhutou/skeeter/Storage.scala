package org.zhutou.skeeter

import redis.clients.jedis.Jedis
import collection.JavaConversions._
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer

abstract class Storage {
  def save(message: MQTTPublishMessage): String

  def load(id: String): MQTTPublishMessage

  def subscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String)

  def getSubscribers(topicName: String): List[String]

  def getSubscribedTopics(clientId: String): List[String]

  def addToInbox(clientId: String, messageId: String)

  def getFromInbox(clientId: String): String
}

object RedisStorage extends Storage {
  val jedis = new Jedis(Config.redisAddress, Config.redisPort)
  val serialization = new JdkSerializationRedisSerializer

  def generateId(): String = jedis.incr(Config.redisKeyPrefix + "id").toString

  private def getTopicSubscriberKey(topicName: String) = (Config.redisKeyPrefix + ":topic:" + topicName)

  private def getClientSubscribedKey(clientId: String) = (Config.redisKeyPrefix + ":client:" + clientId)

  private def getClientInFlightMessagesKey(clientId: String) = (Config.redisKeyPrefix + ":inflight:client:" + clientId)

  private def getPayloadKey(id: String) = (Config.redisKeyPrefix + ":payload:" + id).getBytes("UTF-8")

  override def save(message: MQTTPublishMessage): String = {
    val id = generateId()
    jedis.set(getPayloadKey(id), serialization.serialize(message))
    id
  }

  override def load(id: String): MQTTPublishMessage = serialization.deserialize(jedis.get(getPayloadKey(id))).asInstanceOf[MQTTPublishMessage]

  override def subscribe(clientId: String, topicNames: List[String]) = {
    jedis.sadd(getClientSubscribedKey(clientId), topicNames: _*)
    topicNames.foreach {
      topicName => jedis.sadd(getTopicSubscriberKey(topicName), clientId)
    }
  }

  override def unSubscribe(clientId: String, topicNames: List[String]) = {
    jedis.srem(getClientSubscribedKey(clientId), topicNames: _*)
    for (topicName <- topicNames) {
      jedis.srem(getTopicSubscriberKey(topicName), clientId)
    }
  }

  override def unSubscribe(clientId: String) = {
    for (topicName <- jedis.smembers(clientId)) {
      jedis.srem(getTopicSubscriberKey(topicName), clientId)
    }
    jedis.del(getClientSubscribedKey(clientId))

  }

  override def getSubscribers(topicName: String) = jedis.smembers(getTopicSubscriberKey(topicName)).toList

  override def getSubscribedTopics(clientId: String) = jedis.smembers(getClientSubscribedKey(clientId)).toList

  override def getFromInbox(clientId: String): String = jedis.lpop(getClientInFlightMessagesKey(clientId))

  override def addToInbox(clientId: String, messageId: String) = jedis.lpush(getClientInFlightMessagesKey(clientId), messageId)
}

