package cn.tursom.im

import cn.tursom.core.util.base62
import cn.tursom.core.context.ArrayContextEnv
import cn.tursom.core.context.ContextKey
import cn.tursom.core.encrypt.PublicKeyEncrypt
import cn.tursom.core.util.seconds
import cn.tursom.im.ext.LiveDanmuRecordHandler
import cn.tursom.im.ext.LiveDanmuRecordListHandler
import cn.tursom.im.protobuf.TursomSystemMsg
import com.google.protobuf.Message
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull


val callContextEnv = ArrayContextEnv()

val callSignerKey = callContextEnv.newKey<PublicKeyEncrypt>()
val requireSignKey = callContextEnv.newKey<Boolean>().withDefault { false }

suspend inline fun <T> ImWebSocketClient.imRemoteCall(
  receiver: String,
  ext: Message,
  timeoutMs: Long = 5.seconds().toMillis(),
  signer: PublicKeyEncrypt? = null,
  reqId: String = imSnowflake.id.base62(),
  registerHandler: (resume: suspend (T) -> Unit) -> Unit,
): T? {
  val retChannel = Channel<T>(1)
  registerHandler { ret ->
    try {
      retChannel.send(ret)
    } catch (_: Exception) {
    }
  }
  val receiveMsg = withTimeoutOrNull(timeoutMs) {
    if (signer != null) {
      call(receiver, ext.toSingedMsg(signer), reqId)
    } else {
      call(receiver, ext, reqId)
    }
  } ?: return null
  if (!receiveMsg.sendMsgResponse.success) return null
  val ret = withTimeoutOrNull(timeoutMs) { retChannel.receive() }
  retChannel.close()
  return ret
}

suspend fun ImWebSocketClient.callGetLiveDanmuRecordList(
  receiver: String,
  roomId: String,
  skip: Int = 0,
  limit: Int = 0,
  timeoutMs: Long = 5.seconds().toMillis(),
  signer: PublicKeyEncrypt? = null,
): TursomSystemMsg.ReturnLiveDanmuRecordList? {
  val callReqId = imSnowflake.id.base62()
  return imRemoteCall(
    receiver,
    TursomSystemMsg.GetLiveDanmuRecordList.newBuilder()
      .setReqId(callReqId)
      .setRoomId(roomId)
      .setSkip(skip)
      .setLimit(limit)
      .build(),
    timeoutMs,
    signer
  ) { cont ->
    LiveDanmuRecordListHandler.tempHandle(handler.system, callReqId) { listenLiveRoom, _ ->
      cont(listenLiveRoom)
    }
  }
}

suspend fun ImWebSocketClient.callGetLiveDanmuRecord(
  receiver: String,
  liveDanmuRecordId: String,
  timeoutMs: Long = 5.seconds().toMillis(),
): TursomSystemMsg.ReturnLiveDanmuRecord? {
  val callReqId = imSnowflake.id.base62()
  return imRemoteCall(
    receiver,
    TursomSystemMsg.GetLiveDanmuRecord.newBuilder()
      .setReqId(callReqId)
      .setLiveDanmuRecordId(liveDanmuRecordId)
      .build(),
    timeoutMs
  ) { cont ->
    LiveDanmuRecordHandler.tempHandle(handler.system, callReqId) { listenLiveRoom, _ ->
      cont(listenLiveRoom)
    }
  }
}
