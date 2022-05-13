package cn.tursom.im

import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomMsg.MsgContent
import com.google.protobuf.Message

class BroadcastMsgSender(
  override val client: ImWebSocketClient,
  override val receiveMsg: TursomMsg.ImMsg,
) : MsgSender {
  override val sender: String = receiveMsg.broadcast.sender
  override val channel: Int = receiveMsg.broadcast.channel
  override val content: MsgContent = receiveMsg.broadcast.content

  override suspend fun send(msg: Message) {
    when (msg) {
      is TursomMsg.SignedMsg -> client.sendBroadcast(channel, msg)
      is TursomMsg.EncryptMsg -> client.sendBroadcast(channel, msg)
      else -> client.sendBroadcast(channel, msg)
    }
  }
}