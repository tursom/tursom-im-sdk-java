package cn.tursom.im

import cn.tursom.core.Snowflake
import cn.tursom.core.base62
import cn.tursom.core.seconds
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomSystemMsg
import com.google.protobuf.Message
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

val imSnowflake = Snowflake(0)

fun connect(
  url: String,
  token: String,
  onLogin: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)? = null,
): ImWebSocketClient {
  val webSocketClient = ImWebSocketClient(url, token)
  if (onLogin != null) {
    webSocketClient.handler.handleMsg(TursomMsg.ImMsg.ContentCase.LOGINRESULT, onLogin)
  }
  webSocketClient.open()
  return webSocketClient
}

suspend fun <T> ImWebSocketClient.imRemoteCall(
  receiver: String,
  ext: Message,
  timeoutMs: Long = 5.seconds().toMillis(),
  registerHandler: (resume: suspend (T) -> Unit) -> Unit,
): T? {
  val retChannel = Channel<T>(1)
  registerHandler { ret ->
    try {
      retChannel.send(ret)
    } catch (e: Exception) {
    }
  }
  val receiveMsg = withTimeoutOrNull(timeoutMs) {
    call(receiver, ext)
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
    timeoutMs
  ) { cont ->
    handler.system.addLiveDanmuRecordListHandler(callReqId) { _, _, listenLiveRoom ->
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
    handler.system.addLiveDanmuRecordHandler(callReqId) { _, _, listenLiveRoom ->
      cont(listenLiveRoom)
    }
  }
}
