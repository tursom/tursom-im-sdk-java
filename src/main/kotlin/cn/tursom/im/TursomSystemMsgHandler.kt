package cn.tursom.im

import cn.tursom.core.reflect.MethodInspector
import cn.tursom.core.uncheckedCast
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.log.impl.Slf4jImpl
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.protobuf.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Suppress("MemberVisibilityCanBePrivate", "unused")
class TursomSystemMsgHandler(
  imWebSocketHandler: ImWebSocketHandler? = null,
) {
  companion object : Slf4jImpl()

  private val handlerMap = ConcurrentHashMap<Class<out Message>,
    suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      unpackMsg: Any,
    ) -> Unit>()

  private val msgContextHandlerMap: Cache<TursomMsg.MsgContent.ContentCase,
    suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
    ) -> Unit> =
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()

  private val liveDanmuRecordListHandlerMap: Cache<String,
    suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
    ) -> Unit> =
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()

  private val liveDanmuRecordHandlerMap: Cache<String,
    suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
    ) -> Unit> =
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()

  var default: suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit = { client, receiveMsg ->
    msgContextHandlerMap.getIfPresent(receiveMsg.broadcast.content.contentCase)?.invoke(client, receiveMsg)
  }

  init {
    if (imWebSocketHandler != null) {
      registerToImWebSocketHandler(imWebSocketHandler)
    }

    registerReturnLiveDanmuRecordListHandler { client, receiveMsg, listenLiveRoom ->
      val handler = liveDanmuRecordListHandlerMap.getIfPresent(listenLiveRoom.reqId)
      handler?.invoke(client, receiveMsg, listenLiveRoom)
    }
    registerReturnLiveDanmuRecordHandler { client, receiveMsg, listenLiveRoom ->
      val handler = liveDanmuRecordHandlerMap.getIfPresent(listenLiveRoom.reqId)
      handler?.invoke(client, receiveMsg, listenLiveRoom)
    }
  }

  /**
   * 解析对象的所有方法，并将该方法注册为处理方法
   * 方法签名为:
   * suspend fun 方法名(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg, msg: T)
   * T 为 Message 的子类
   */
  fun registerHandlerObject(handler: Any) {
    MethodInspector.forEachSuspendMethod(
      handler,
      Unit::class.java,
      ImWebSocketClient::class.java,
      TursomMsg.ImMsg::class.java,
      Message::class.java
    ) { method, handlerCallback ->
      registerHandler(method.parameterTypes[method.parameterTypes.size - 2].uncheckedCast(), handlerCallback)
    }
  }

  fun default(handler: suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit) {
    default = handler
  }

  fun handle(
    contentCase: TursomMsg.MsgContent.ContentCase,
    handler: suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit,
  ) {
    msgContextHandlerMap.put(contentCase, handler)
  }

  fun registerToImWebSocketHandler(imWebSocketHandler: ImWebSocketHandler) {
    imWebSocketHandler.handleMsg(TursomMsg.ImMsg.ContentCase.CHATMSG, ::handle)
    imWebSocketHandler.system = this
  }

  fun addLiveDanmuRecordListHandler(
    reqId: String,
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
    ) -> Unit,
  ) {
    liveDanmuRecordListHandlerMap.put(reqId, handler)
  }

  fun addLiveDanmuRecordHandler(
    reqId: String,
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
    ) -> Unit,
  ) {
    liveDanmuRecordHandlerMap.put(reqId, handler)
  }

  suspend fun handle(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) {
    if (receiveMsg.contentCase != TursomMsg.ImMsg.ContentCase.CHATMSG ||
      receiveMsg.chatMsg.content.contentCase != TursomMsg.MsgContent.ContentCase.EXT
    ) {
      default.invoke(client, receiveMsg)
      return
    }

    val ext = receiveMsg.chatMsg.content.ext
    handleExt(client, receiveMsg, ext)
  }

  suspend fun handleBroadcast(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) {
    if (receiveMsg.contentCase != TursomMsg.ImMsg.ContentCase.BROADCAST ||
      receiveMsg.broadcast.content.contentCase != TursomMsg.MsgContent.ContentCase.EXT
    ) {
      default.invoke(client, receiveMsg)
      return
    }

    val ext = receiveMsg.chatMsg.content.ext
    handleExt(client, receiveMsg, ext)
  }

  suspend fun handleExt(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg, ext: com.google.protobuf.Any) {
    handlerMap.forEach { (clazz, handler) ->
      if (!ext.`is`(clazz)) {
        return@forEach
      }
      @Suppress("BlockingMethodInNonBlockingContext")
      val unpackMsg = ext.unpack(clazz)
      handler(client, receiveMsg, unpackMsg)
      return
    }

    logger.warn("unknown ext type: {}", ext.typeUrl)
    return
  }

  inline fun <reified T : Message> registerHandler(
    noinline handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      unpackMsg: T,
    ) -> Unit,
  ) = registerHandler(T::class.java, handler)

  fun <T : Message> registerHandler(
    clazz: Class<T>,
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      unpackMsg: T,
    ) -> Unit,
  ) {
    handlerMap[clazz] = handler.uncheckedCast()
  }

  fun registerListenLiveRoomHandler(
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ListenLiveRoom,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerAddMailReceiverHandler(
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.AddMailReceiver,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerGetLiveDanmuRecordListHandler(
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.GetLiveDanmuRecordList,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerReturnLiveDanmuRecordListHandler(
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerGetLiveDanmuRecordHandler(
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.GetLiveDanmuRecord,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerReturnLiveDanmuRecordHandler(
    handler: suspend (
      client: ImWebSocketClient,
      receiveMsg: TursomMsg.ImMsg,
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
    ) -> Unit,
  ) = registerHandler(handler)
}