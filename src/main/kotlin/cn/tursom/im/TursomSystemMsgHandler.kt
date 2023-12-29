package cn.tursom.im

import cn.tursom.core.context.ArrayContextEnv
import cn.tursom.core.context.ContextEnv
import cn.tursom.core.util.uncheckedCast
import cn.tursom.im.ext.LiveDanmuRecordHandler
import cn.tursom.im.ext.LiveDanmuRecordListHandler
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.log.impl.Slf4jImpl
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.protobuf.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * @param imWebSocketHandler register to [imWebSocketHandler]'s [TursomMsg.ImMsg.ContentCase.CHATMSG] handler
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
open class TursomSystemMsgHandler(
  private val signerContainer: SignerContainer,
  private val encryptDecoder: EncryptDecoder,
) : TursomSystemMsgHandlerRegister {
  companion object : Slf4jImpl() {
    val ctxEnv: ContextEnv = ArrayContextEnv()
  }

  val handlerTypeUrlSet: Set<String>
    get() {
      val handlerTypeUrlSet = HashSet<String>()
      extHandlerMap.forEach { (clazz, _) ->
        handlerTypeUrlSet.add(AnyUtils.getTypeUrl(clazz))
      }
      return handlerTypeUrlSet
    }

  private val extHandlerMap = ConcurrentHashMap<Class<out Message>,
    suspend (
      unpackMsg: Any,
      msgSender: MsgSender,
    ) -> Unit>()

  private val handlerMap = ConcurrentHashMap<TursomMsg.MsgContent.ContentCase,
    suspend (
      msgSender: MsgSender,
    ) -> Unit>()

  private val tempHandlerMap: Cache<TursomMsg.MsgContent.ContentCase,
    suspend (
      msgSender: MsgSender,
    ) -> Unit> = Caffeine.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build()

  val ctx = ctxEnv.newContext()

  var default: suspend (client: ImWebSocketClient, msgSender: MsgSender) -> Unit = { client, msgSender ->
    (tempHandlerMap.getIfPresent(msgSender.content.contentCase) ?: handlerMap[msgSender.content.contentCase])
      ?.invoke(msgSender)
  }

  init {
    LiveDanmuRecordListHandler.register(this)
    LiveDanmuRecordHandler.register(this)
  }

  fun default(handler: suspend (client: ImWebSocketClient, msgSender: MsgSender) -> Unit) {
    default = handler
  }

  override fun <T : Message> registerHandler(
    clazz: Class<T>,
    handler: suspend (
      unpackMsg: T,
      msgSender: MsgSender,
    ) -> Unit,
  ) {
    extHandlerMap[clazz] = handler.uncheckedCast()
  }

  fun registerHandler(
    contentCase: TursomMsg.MsgContent.ContentCase,
    handler: suspend (
      msgSender: MsgSender,
    ) -> Unit,
  ) {
    handlerMap[contentCase] = handler
  }

  fun registerTempHandler(
    contentCase: TursomMsg.MsgContent.ContentCase,
    handler: suspend (
      msgSender: MsgSender,
    ) -> Unit,
  ) {
    tempHandlerMap.put(contentCase, handler)
  }

  suspend fun handleChat(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) {
    if (receiveMsg.contentCase != TursomMsg.ImMsg.ContentCase.CHATMSG) {
      throw UnsupportedOperationException()
    }

    val content = receiveMsg.chatMsg.content
    when (content.contentCase) {
      TursomMsg.MsgContent.ContentCase.EXT -> {
        val ext = content.ext
        handleExt(ext, SystemMsgSender(client, receiveMsg))
      }
      TursomMsg.MsgContent.ContentCase.SIGNED -> {
        val signed = content.signed
        if (!content.signed.check(signerContainer)) {
          return
        }

        val ext = com.google.protobuf.Any.parseFrom(signed.msg)
        handleExt(ext, SystemMsgSender(client, receiveMsg))
      }
      TursomMsg.MsgContent.ContentCase.ENCRYPT -> {
        val bytes = encryptDecoder.decode(content.encrypt) ?: return
        val ext = com.google.protobuf.Any.parseFrom(bytes)
        handleExt(ext, SystemMsgSender(client, receiveMsg))
      }
      else -> default.invoke(client, SystemMsgSender(client, receiveMsg))
    }
  }

  suspend fun handleBroadcast(client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) {
    if (receiveMsg.contentCase != TursomMsg.ImMsg.ContentCase.BROADCAST) {
      throw UnsupportedOperationException()
    }

    val content = receiveMsg.broadcast.content
    when (content.contentCase) {
      TursomMsg.MsgContent.ContentCase.EXT -> {
        val ext = content.ext
        handleExt(ext, BroadcastMsgSender(client, receiveMsg))
      }
      TursomMsg.MsgContent.ContentCase.SIGNED -> {
        val signed = content.signed
        if (!content.signed.check(signerContainer)) {
          return
        }

        val ext = com.google.protobuf.Any.parseFrom(signed.msg)
        handleExt(ext, BroadcastMsgSender(client, receiveMsg))
      }
      else -> default.invoke(client, BroadcastMsgSender(client, receiveMsg))
    }
  }

  suspend fun handleExt(
    ext: com.google.protobuf.Any,
    msgSender: MsgSender,
  ) {
    extHandlerMap.forEach { (clazz, handler) ->
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
}
