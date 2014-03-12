package org.zhutou.skeeter

import redis.clients.jedis.{JedisPool, Jedis}
import collection.JavaConversions._
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer

abstract class Storage {
  def save(message: MQTTPublishMessage): String

  def load(id: String): MQTTPublishMessage

  def subscribe(clientId: String, subscriptions: List[MessageSubscription])

  def unSubscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String): List[String]

  def getSubscribers(topicName: String): List[MessageSubscription]

  def getSubscribedTopics(clientId: String): List[String]

  def addToInbox(clientId: String, messageId: String)

  def getFromInbox(clientId: String): String

  def flushInbox(clientId: String)
}

object RedisStorage extends Storage {
  val jedisPool = new JedisPool(Config.redisAddress, Config.redisPort)
  val serialization = new JdkSerializationRedisSerializer

  private def getTopicSubscriberKey(topicName: String) = Config.redisKeyPrefix + ":topic:" + topicName

  private def getClientSubscribedKey(clientId: String) = Config.redisKeyPrefix + ":client:" + clientId

  private def getClientSubscriptionKey(topicName: String, clientId: String) = Config.redisKeyPrefix + ":subscription:" + topicName + ":" + clientId

  private def getClientInFlightMessagesKey(clientId: String) = Config.redisKeyPrefix + ":inflight:client:" + clientId

  private def getPayloadKey(id: String) = (Config.redisKeyPrefix + ":payload:" + id).getBytes("UTF-8")

  private def withJedis(func: (Jedis) => Unit) {
    val jedis = jedisPool.getResource
    try {
      func(jedis)
    } finally {
      jedisPool.returnResourceObject(jedis)
    }
  }

  override def save(message: MQTTPublishMessage): String = {
    var messageId: String = ""
    withJedis {
      jedis =>
        messageId = jedis.incr(Config.redisKeyPrefix + ":id").toString
        jedis.set(getPayloadKey(messageId), serialization.serialize(message))
    }
    messageId
  }

  override def load(id: String): MQTTPublishMessage = {
    var message: MQTTPublishMessage = null
    withJedis {
      jedis =>
        message = serialization.deserialize(jedis.get(getPayloadKey(id))).asInstanceOf[MQTTPublishMessage]
    }
    message
  }

  override def subscribe(clientId: String, subscriptions: List[MessageSubscription]) = {
    withJedis {
      jedis =>
        jedis.sadd(getClientSubscribedKey(clientId), subscriptions.map(_.mTopicName): _*)
        subscriptions.foreach {
          s =>
            jedis.sadd(getTopicSubscriberKey(s.mTopicName), clientId)
            jedis.set(getClientSubscriptionKey(s.mTopicName, clientId), s.mQoSLevel.toString)
        }
    }
  }

  override def unSubscribe(clientId: String, topicNames: List[String]) = {
    withJedis {
      jedis =>
        jedis.srem(getClientSubscribedKey(clientId), topicNames: _*)
        topicNames.foreach {
          topicName =>
            jedis.srem(getTopicSubscriberKey(topicName), clientId)
            jedis.del(getClientSubscriptionKey(topicName, clientId))
        }
    }
  }

  override def unSubscribe(clientId: String): List[String] = {
    var subscribedTopicNames: List[String] = Nil
    withJedis {
      jedis =>
        subscribedTopicNames = jedis.smembers(clientId).toList
        for (topicName <- subscribedTopicNames) {
          jedis.srem(getTopicSubscriberKey(topicName), clientId)
        }
        jedis.del(getClientSubscribedKey(clientId))
    }
    subscribedTopicNames
  }

  override def getSubscribers(topicName: String) = {
    var subscribers: List[MessageSubscription] = Nil
    withJedis {
      jedis =>
        val clientIds = jedis.smembers(getTopicSubscriberKey(topicName))
        subscribers = clientIds.map(clientId => MessageSubscription(clientId, topicName,
          jedis.get(getClientSubscriptionKey(topicName, clientId)).toByte)).toList
    }
    subscribers
  }

  override def getSubscribedTopics(clientId: String) = {
    var subscribedTopics: List[String] = Nil
    withJedis {
      jedis =>
        subscribedTopics = jedis.smembers(getClientSubscribedKey(clientId)).toList
    }
    subscribedTopics
  }

  override def getFromInbox(clientId: String): String = {
    var messageId: String = ""
    withJedis {
      jedis =>
        messageId = jedis.lpop(getClientInFlightMessagesKey(clientId))
    }
    messageId
  }

  override def addToInbox(clientId: String, messageId: String) = {
    withJedis {
      jedis =>
        jedis.lpush(getClientInFlightMessagesKey(clientId), messageId)
    }
  }

  override def flushInbox(clientId: String) = {
    withJedis {
      jedis =>
        jedis.del(getClientInFlightMessagesKey(clientId))
    }
  }
}

