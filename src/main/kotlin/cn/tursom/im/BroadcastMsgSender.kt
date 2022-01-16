package cn.tursom.im

import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Message

class BroadcastMsgSender(
  override val client: ImWebSocketClient,
  override val receiveMsg: TursomMsg.ImMsg,
) : MsgSender {
  override val sender: String = receiveMsg.broadcast.sender
  override val channel: Int = receiveMsg.broadcast.channel
  override val reqId: String = receiveMsg.broadcast.reqId

  override suspend fun send(msg: Message) {
    client.sendBroadcast(channel, msg)
  }
}