package cn.tursom.im

import cn.tursom.core.seconds
import cn.tursom.core.ws.AbstractWebSocketHandler
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.log.impl.Slf4jImpl
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

typealias ImMsgHandler = suspend (client: ImWebSocketClient, imMsg: TursomMsg.ImMsg) -> Unit

open class ImWebSocketHandler(
  private val token: String,
  var heartbeatInterval: Long = 30.seconds().toMillis(),
  private val currentNodeName: String = "",
  signerContainer: SignerContainer,
  encryptDecoder: EncryptDecoder,
) : AbstractWebSocketHandler<ImWebSocketClient, ImWebSocketHandler>() {
  companion object : Slf4jImpl()

  @Retention(AnnotationRetention.BINARY)
  @Target(AnnotationTarget.FUNCTION)
  annotation class Handler(val contentCase: TursomMsg.ImMsg.ContentCase)

  private val handlerMap = HashMap<TursomMsg.ImMsg.ContentCase, MutableCollection<ImMsgHandler>>()
  private val chatHandlerMap = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<String, ImMsgHandler>()
  private val broadcastResponseHandlerMap = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build<String, ImMsgHandler>()
  private val broadcastHandlerMap = HashMap<Int, ImMsgHandler>()

  val system = TursomSystemMsgHandler(signerContainer, encryptDecoder)

  /**
   * default broadcast msg handler
   */
  val broadcast = TursomSystemMsgHandler(signerContainer, encryptDecoder)

  var onMsgRead: (suspend (TursomMsg.ImMsg) -> Unit)? = null

  init {
    registerBaseHandlers()
  }

  fun registerMsgHandle(
    case: TursomMsg.ImMsg.ContentCase,
    handler: ImMsgHandler,
  ): HandlerRemover {
    return registerHandler(handlerMap, case, handler)
  }

  fun registerChatMsgResultHandler(
    reqId: String,
    handler: ImMsgHandler?,
  ) {
    if (handler == null) {
      chatHandlerMap.invalidate(reqId)
    } else {
      chatHandlerMap.put(reqId, handler)
    }
  }

  fun registerSendBroadcastResultHandler(
    reqId: String,
    handler: ImMsgHandler?,
  ) {
    if (handler == null) {
      broadcastResponseHandlerMap.invalidate(reqId)
    } else {
      broadcastResponseHandlerMap.put(reqId, handler)
    }
  }

  fun registerSendBroadcastHandler(
    channel: Int,
    handler: ImMsgHandler?,
  ) {
    handler ?: return
    broadcastHandlerMap[channel] = handler
  }

  private fun <K> registerHandler(
    handlerMap: MutableMap<K, MutableCollection<ImMsgHandler>>,
    key: K,
    handler: ImMsgHandler,
  ): HandlerRemover {
    var handlerCollection = handlerMap[key]
    if (handlerCollection == null) {
      synchronized(handlerMap) {
        handlerCollection = handlerMap[key]
        if (handlerCollection == null) {
          handlerCollection = ConcurrentLinkedQueue()
          handlerMap[key] = handlerCollection!!
        }
      }
    }

    handlerCollection!!.add(handler)
    return HandlerRemover {
      handlerCollection!!.remove(handler)
    }
  }

  override fun onOpen(client: ImWebSocketClient) {
    val loginRequest = TursomMsg.LoginRequest.newBuilder()
      .setToken(token)
      .build()
    val imMsg = TursomMsg.ImMsg.newBuilder()
      .setLoginRequest(loginRequest)
      .build()
    client.write(imMsg.toByteArray())
  }

  override fun readMessage(client: ImWebSocketClient, msg: ByteArray) {
    client.coroutineScope.launch {
      @Suppress("BlockingMethodInNonBlockingContext")
      val imMsg = TursomMsg.ImMsg.parseFrom(msg)
      debug("im msg read: {}", imMsg)

      onMsgRead?.invoke(imMsg)

      handlerMap[imMsg.contentCase]?.forEach { handler ->
        try {
          handler(client, imMsg)
        } catch (e: Exception) {
          log.error("an exception caused on handle im msg", e)
        }
      }
    }
  }

  /**
   * [loginHandler] to init client value after get login response
   */
  @Handler(TursomMsg.ImMsg.ContentCase.LOGINRESULT)
  private suspend fun loginHandler(client: ImWebSocketClient, imMsg: TursomMsg.ImMsg) {
    client.currentUserId = imMsg.loginResult.imUserId
    client.login = imMsg.loginResult.success
    client.onLogin = false
    if (imMsg.loginResult.success) {
      client.allocateNode(currentNodeName)

      @OptIn(ObsoleteCoroutinesApi::class)
      client.coroutineScope.launch {
        val ticker = ticker(heartbeatInterval)
        try {
          while (true) {
            ticker.receive()
            client.write(
              TursomMsg.ImMsg.newBuilder()
                .setHeartBeat(System.currentTimeMillis().toString())
                .build()
                .toByteArray()
            )
          }
        } finally {
          ticker.cancel()
        }
      }
    }
  }

  private fun registerBaseHandlers() {
    registerMsgHandle(TursomMsg.ImMsg.ContentCase.LOGINRESULT, ::loginHandler)
    registerMsgHandle(TursomMsg.ImMsg.ContentCase.CHATMSG, system::handleChat)

    registerMsgHandle(TursomMsg.ImMsg.ContentCase.SENDMSGRESPONSE) { client, receiveMsg ->
      val reqId = receiveMsg.sendMsgResponse.reqId
      val handler = chatHandlerMap.getIfPresent(reqId)
      handler?.invoke(client, receiveMsg)
    }

    registerMsgHandle(TursomMsg.ImMsg.ContentCase.SENDBROADCASTRESPONSE) { client, receiveMsg ->
      val reqId = receiveMsg.sendBroadcastResponse.reqId
      val handler = broadcastResponseHandlerMap.getIfPresent(reqId)
      handler?.invoke(client, receiveMsg)
    }

    registerMsgHandle(TursomMsg.ImMsg.ContentCase.BROADCAST) { client, receiveMsg ->
      val channel = receiveMsg.broadcast.channel
      val handler = broadcastHandlerMap[channel]
      handler?.invoke(client, receiveMsg)
    }
  }
}