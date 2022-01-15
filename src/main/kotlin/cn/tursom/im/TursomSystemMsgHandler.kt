package cn.tursom.im

import cn.tursom.core.reflect.MethodInspector
import cn.tursom.core.reflect.getType
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
  companion object : Slf4jImpl() {
  }

  val handlerTypeUrlSet: Set<String>
    get() {
      val handlerTypeUrlSet = HashSet<String>()
      handlerMap.forEach { (clazz, _) ->
        handlerTypeUrlSet.add(AnyUtils.getTypeUrl(clazz))
      }
      return handlerTypeUrlSet
    }

  private val handlerMap = ConcurrentHashMap<Class<out Message>,
    suspend (
      unpackMsg: Any,
      msgSender: MsgSender,
    ) -> Unit>()

  private val msgContextHandlerMap: Cache<TursomMsg.MsgContent.ContentCase,
    suspend (
      msgSender: MsgSender,
    ) -> Unit> =
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()

  private val liveDanmuRecordListHandlerMap: Cache<String,
    suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
      msgSender: MsgSender,
    ) -> Unit> =
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()

  private val liveDanmuRecordHandlerMap: Cache<String,
    suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
      msgSender: MsgSender,
    ) -> Unit> =
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()

  var default: suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit = { client, receiveMsg ->
    msgContextHandlerMap.getIfPresent(receiveMsg.broadcast.content.contentCase)
      ?.invoke(BroadcastMsgSender(client, receiveMsg))
  }

  init {
    if (imWebSocketHandler != null) {
      registerToImWebSocketHandler(imWebSocketHandler)
    }

    registerReturnLiveDanmuRecordListHandler { listenLiveRoom, msgSender ->
      val handler = liveDanmuRecordListHandlerMap.getIfPresent(listenLiveRoom.reqId)
      handler?.invoke(listenLiveRoom, msgSender)
    }
    registerReturnLiveDanmuRecordHandler { listenLiveRoom, msgSender ->
      val handler = liveDanmuRecordHandlerMap.getIfPresent(listenLiveRoom.reqId)
      handler?.invoke(listenLiveRoom, msgSender)
    }
  }

  /**
   * 解析对象的所有方法，并将该方法注册为处理方法
   * 方法签名为:
   * suspend fun 方法名(
   *     msg: T,
   *     msgSender: MsgSender,
   * )
   * T 为 Message 的子类
   */
  fun registerHandlerObject(handler: Any) {
    MethodInspector.forEachSuspendMethod(
      handler,
      Unit::class.java,
      Message::class.java,
      getType<MsgSender>()
    ) { method, handlerCallback ->
      registerHandler(method.parameterTypes[method.parameterTypes.size - 2].uncheckedCast(), handlerCallback)
    }
  }

  fun default(handler: suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit) {
    default = handler
  }

  fun handle(
    contentCase: TursomMsg.MsgContent.ContentCase,
    handler: suspend (
      msgSender: MsgSender,
    ) -> Unit,
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
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
      msgSender: MsgSender,
    ) -> Unit,
  ) {
    liveDanmuRecordListHandlerMap.put(reqId, handler)
  }

  fun addLiveDanmuRecordHandler(
    reqId: String,
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
      msgSender: MsgSender,
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
    handleExt(ext, SystemMsgSender(client, receiveMsg))
  }

  suspend fun handleBroadcast(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) {
    if (receiveMsg.contentCase != TursomMsg.ImMsg.ContentCase.BROADCAST ||
      receiveMsg.broadcast.content.contentCase != TursomMsg.MsgContent.ContentCase.EXT
    ) {
      default.invoke(client, receiveMsg)
      return
    }

    val ext = receiveMsg.chatMsg.content.ext
    handleExt(ext, BroadcastMsgSender(client, receiveMsg))
  }

  suspend fun handleExt(
    ext: com.google.protobuf.Any,
    msgSender: MsgSender,
  ) {
    handlerMap.forEach { (clazz, handler) ->
      if (!ext.`is`(clazz)) {
        return@forEach
      }
      @Suppress("BlockingMethodInNonBlockingContext")
      val unpackMsg = ext.unpack(clazz)
      handler(unpackMsg, msgSender)
      return
    }

    logger.warn("unknown ext type: {}", ext.typeUrl)
    return
  }

  inline fun <reified T : Message> registerHandler(
    noinline handler: suspend (
      unpackMsg: T,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(T::class.java, handler)

  fun <T : Message> registerHandler(
    clazz: Class<T>,
    handler: suspend (
      unpackMsg: T,
      msgSender: MsgSender,
    ) -> Unit,
  ) {
    handlerMap[clazz] = handler.uncheckedCast()
  }

  fun registerListenLiveRoomHandler(
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.ListenLiveRoom,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerAddMailReceiverHandler(
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.AddMailReceiver,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerGetLiveDanmuRecordListHandler(
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.GetLiveDanmuRecordList,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerReturnLiveDanmuRecordListHandler(
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerGetLiveDanmuRecordHandler(
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.GetLiveDanmuRecord,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(handler)

  fun registerReturnLiveDanmuRecordHandler(
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
      msgSender: MsgSender,
    ) -> Unit,
  ) = registerHandler(handler)
}