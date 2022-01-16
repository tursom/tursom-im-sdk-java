package cn.tursom.im

import cn.tursom.core.Snowflake
import cn.tursom.core.base62
import cn.tursom.core.delegation.ext.NettyAttributeDelegation.Companion.attributeDelegation
import cn.tursom.core.ws.WebSocketClient
import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Any
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import com.google.protobuf.MessageLite
import io.netty.channel.ChannelFuture
import io.netty.channel.socket.SocketChannel
import io.netty.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("unused")
open class ImWebSocketClient(
  url: String,
  handler: ImWebSocketHandler,
  autoWrap: Boolean = true,
  log: Boolean = false,
  compressed: Boolean = true,
  maxContextLength: Int = 4096,
  headers: Map<String, String>? = null,
  handshakerUri: URI? = null,
  autoRelease: Boolean = true,
  initChannel: ((ch: SocketChannel) -> Unit)? = null,
) : WebSocketClient<ImWebSocketClient, ImWebSocketHandler>(
  url, handler, autoWrap, log, compressed, maxContextLength,
  headers, handshakerUri, autoRelease, initChannel
) {
  companion object {
    private val currentUserIdAttr: AttributeKey<String?> =
      AttributeKey.newInstance("currentUserId")
  }

  constructor(
    url: String,
    token: String,
    autoWrap: Boolean = true,
    log: Boolean = false,
    compressed: Boolean = true,
    maxContextLength: Int = 4096,
    headers: Map<String, String>? = null,
    handshakerUri: URI? = null,
    autoRelease: Boolean = true,
    initChannel: ((ch: SocketChannel) -> Unit)? = null,
  ) : this(
    url,
    ImWebSocketHandler(token),
    autoWrap,
    log,
    compressed,
    maxContextLength,
    headers,
    handshakerUri,
    autoRelease,
    initChannel
  )

  var imSnowflake = Snowflake(0)
    internal set
  val coroutineScope = CoroutineScope(Dispatchers.Default)
  var currentUserId by attributeDelegation(currentUserIdAttr, ::ch)
    internal set
  var login = false
    internal set

  fun write(msg: MessageLite): ChannelFuture {
    return write(msg.toByteArray())
  }

  fun sendExtMsg(
    receiver: String,
    ext: Message,
    reqId: String = imSnowflake.id.base62(),
  ): ChannelFuture {
    return write(
      TursomMsg.ImMsg.newBuilder()
        .setSendMsgRequest(
          TursomMsg.SendMsgRequest.newBuilder()
            .setReceiver(receiver)
            .setReqId(reqId)
            .setContent(
              TursomMsg.MsgContent.newBuilder()
                .setExt(Any.pack(ext))
            )
        )
        .build()
    )
  }

  fun sendExtMsg(
    receiver: String,
    ext: Message.Builder,
    reqId: String = imSnowflake.id.base62(),
  ): ChannelFuture = sendExtMsg(receiver, ext.build(), reqId)

  suspend fun call(
    receiver: String,
    ext: Message,
    reqId: String = imSnowflake.id.base62(),
  ): TursomMsg.ImMsg {
    return suspendCoroutine { cont ->
      handler.registerChatMsgResult(reqId) { _, receiveMsg ->
        cont.resume(receiveMsg)
      }
      sendExtMsg(receiver, ext, reqId)
    }
  }

  suspend fun call(
    receiver: String,
    ext: Message.Builder,
    reqId: String = imSnowflake.id.base62(),
  ): TursomMsg.ImMsg = call(receiver, ext.build(), reqId)

  suspend fun listenBroadcast(
    channel: Int,
    msgHandler: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)? = null,
  ): Boolean {
    val reqId = imSnowflake.id.base62()
    // 监听广播
    return suspendCoroutine { cont ->
      handler.handleMsg(TursomMsg.ImMsg.ContentCase.LISTENBROADCASTRESPONSE) { _, receiveMsg ->
        if (receiveMsg.listenBroadcastResponse.success) {
          handler.registerSendBroadcastHandler(
            channel,
            msgHandler ?: handler.broadcast::handleBroadcast
          )
        }
        cont.resume(receiveMsg.listenBroadcastResponse.success)
      }
      write(
        TursomMsg.ImMsg.newBuilder()
          .setListenBroadcastRequest(
            TursomMsg.ListenBroadcastRequest.newBuilder()
              .setReqId(reqId)
              .setChannel(channel)
          )
          .build()
      )
    }
  }

  fun cancelListenBroadcast(
    channel: Int,
  ) {
    val reqId = imSnowflake.id.base62()
    // 取消监听广播
    handler.registerSendBroadcastHandler(channel, null)
    write(
      TursomMsg.ImMsg.newBuilder()
        .setListenBroadcastRequest(
          TursomMsg.ListenBroadcastRequest.newBuilder()
            .setReqId(reqId)
            .setChannel(channel)
            .setCancelListen(true)
        )
        .build()
    )
  }

  @Suppress("UsePropertyAccessSyntax")
  suspend fun sendBroadcast(
    channel: Int,
    msg: TursomMsg.MsgContentOrBuilder,
  ): TursomMsg.ImMsg {
    val reqId = imSnowflake.id.base62()
    return suspendCoroutine { cont ->
      handler.registerSendBroadcastResult(reqId) { _, receiveMsg ->
        cont.resume(receiveMsg)
      }
      write(TursomMsg.ImMsg.newBuilder()
        .setSendBroadcastRequest(
          TursomMsg.SendBroadcastRequest.newBuilder()
            .setReqId(reqId)
            .setChannel(channel)
            .apply {
              if (msg is TursomMsg.MsgContent) {
                setContent(msg)
              } else if (msg is TursomMsg.MsgContent.Builder) {
                setContent(msg)
              }
            }
        )
        .build())
    }
  }

  suspend fun sendBroadcast(
    channel: Int,
    msg: String,
  ): TursomMsg.ImMsg {
    return sendBroadcast(
      channel, TursomMsg.MsgContent.newBuilder()
        .setMsg(msg)
    )
  }

  suspend fun sendBroadcast(
    channel: Int,
    msg: Any,
  ): TursomMsg.ImMsg {
    return sendBroadcast(
      channel, TursomMsg.MsgContent.newBuilder()
        .setExt(msg)
    )
  }

  suspend fun <T : Message> sendBroadcast(
    channel: Int,
    msg: T,
  ): TursomMsg.ImMsg {
    return sendBroadcast(
      channel, TursomMsg.MsgContent.newBuilder()
        .setExt(Any.pack(msg))
    )
  }

  suspend fun <BuilderType : GeneratedMessageV3.Builder<BuilderType>> sendBroadcast(
    channel: Int,
    msg: BuilderType,
  ): TursomMsg.ImMsg {
    return sendBroadcast(
      channel, TursomMsg.MsgContent.newBuilder()
        .setExt(Any.pack(msg.build()))
    )
  }

  suspend fun allocateNode(currentNodeName: String = "") {
    val reqId = imSnowflake.id.base62()
    suspendCoroutine<Unit> { cont ->
      handler.handleMsg(TursomMsg.ImMsg.ContentCase.ALLOCATENODERESPONSE) { _, receiveMsg ->
        handler.handleMsg(TursomMsg.ImMsg.ContentCase.ALLOCATENODERESPONSE, null)

        imSnowflake = Snowflake(receiveMsg.allocateNodeResponse.node)
        cont.resume(Unit)
      }
      write(
        TursomMsg.ImMsg.newBuilder()
          .setAllocateNodeRequest(
            TursomMsg.AllocateNodeRequest.newBuilder()
              .setReqId(reqId)
              .setCurrentNodeName(currentNodeName)
          )
          .build()
      )
    }
  }

  override fun onClose() {
    super.onClose()
    login = false
  }
}