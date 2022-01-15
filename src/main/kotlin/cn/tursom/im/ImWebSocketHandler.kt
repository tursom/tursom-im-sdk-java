package cn.tursom.im

import cn.tursom.core.seconds
import cn.tursom.core.ws.AbstractWebSocketHandler
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.log.impl.Slf4jImpl
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

open class ImWebSocketHandler(
  token: String,
  var heartbeatInterval: Long = 30.seconds().toMillis(),
  currentNodeName: String = "",
) : AbstractWebSocketHandler<ImWebSocketClient, ImWebSocketHandler>() {
  companion object : Slf4jImpl()

  private val handlerMap = ConcurrentHashMap<TursomMsg.ImMsg.ContentCase,
    suspend (client: ImWebSocketClient, imMsg: TursomMsg.ImMsg) -> Unit>()
  private val chatHandlerMap = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<String,
      suspend (client: ImWebSocketClient, imMsg: TursomMsg.ImMsg) -> Unit>()
  private val broadcastResponseHandlerMap = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<String,
      suspend (client: ImWebSocketClient, imMsg: TursomMsg.ImMsg) -> Unit>()
  private val broadcastHandlerMap = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<Int,
      suspend (client: ImWebSocketClient, imMsg: TursomMsg.ImMsg) -> Unit>()

  init {
    onOpen { client ->
      val loginRequest = TursomMsg.LoginRequest.newBuilder()
        .setToken(token)
        .build()
      val imMsg = TursomMsg.ImMsg.newBuilder()
        .setLoginRequest(loginRequest)
        .build()
      client.write(imMsg.toByteArray())
    }

    readMessage { client: ImWebSocketClient, msg: ByteArray ->
      client.coroutineScope.launch {
        @Suppress("BlockingMethodInNonBlockingContext")
        val imMsg = TursomMsg.ImMsg.parseFrom(msg)
        debug("im msg read: {}", imMsg)

        if (imMsg.contentCase == TursomMsg.ImMsg.ContentCase.LOGINRESULT) {
          client.currentUserId = imMsg.loginResult.imUserId
          if (imMsg.loginResult.success) {
            client.allocateNode(currentNodeName)

            client.coroutineScope.launch {
              while (true) {
                delay(heartbeatInterval)
                client.write(
                  TursomMsg.ImMsg.newBuilder()
                    .setHeartBeat(System.currentTimeMillis().toString())
                    .build()
                    .toByteArray()
                )
              }
            }
          }
        }

        handlerMap[imMsg.contentCase]?.invoke(client, imMsg)
      }
    }

    handleMsg(TursomMsg.ImMsg.ContentCase.CHATMSG) { client, receiveMsg ->
      val chatMsg = receiveMsg.chatMsg
      if (chatMsg.content.contentCase != TursomMsg.MsgContent.ContentCase.EXT) {
        return@handleMsg
      }
      system.handle(client, receiveMsg)
    }

    handleMsg(TursomMsg.ImMsg.ContentCase.SENDMSGRESPONSE) { client, receiveMsg ->
      val reqId = receiveMsg.sendMsgResponse.reqId
      val handler = chatHandlerMap.getIfPresent(reqId)
      handler?.invoke(client, receiveMsg)
    }

    handleMsg(TursomMsg.ImMsg.ContentCase.SENDBROADCASTRESPONSE) { client, receiveMsg ->
      val reqId = receiveMsg.sendBroadcastResponse.reqId
      val handler = broadcastResponseHandlerMap.getIfPresent(reqId)
      handler?.invoke(client, receiveMsg)
    }

    handleMsg(TursomMsg.ImMsg.ContentCase.BROADCAST) { client, receiveMsg ->
      val channel = receiveMsg.broadcast.channel
      val handler = broadcastHandlerMap.getIfPresent(channel)
      handler?.invoke(client, receiveMsg)
    }
  }

  var system = TursomSystemMsgHandler(this)
    internal set
  val broadcast = TursomSystemMsgHandler()

  fun handleMsg(
    case: TursomMsg.ImMsg.ContentCase,
    handler: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)?,
  ) {
    if (handler == null) {
      handlerMap.remove(case)
    } else {
      handlerMap[case] = handler
    }
  }

  fun registerChatMsgResult(
    reqId: String,
    handler: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)?,
  ) {
    if (handler == null) {
      chatHandlerMap.invalidate(reqId)
    } else {
      chatHandlerMap.put(reqId, handler)
    }
  }

  fun registerSendBroadcastResult(
    reqId: String,
    handler: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)?,
  ) {
    if (handler == null) {
      broadcastResponseHandlerMap.invalidate(reqId)
    } else {
      broadcastResponseHandlerMap.put(reqId, handler)
    }
  }

  fun registerSendBroadcastHandler(
    channel: Int,
    handler: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)?,
  ) {
    if (handler == null) {
      broadcastHandlerMap.invalidate(channel)
    } else {
      broadcastHandlerMap.put(channel, handler)
    }
  }
}