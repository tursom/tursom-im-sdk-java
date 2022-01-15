package cn.tursom.im

import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Message

class BroadcastMsgSender(
  private val client: ImWebSocketClient,
  receiveMsg: TursomMsg.ImMsg,
) : MsgSender {
  override val sender: String = receiveMsg.broadcast.sender
  override val channel: Int = receiveMsg.broadcast.channel
  override suspend fun invoke(msg: Message) {
    client.sendBroadcast(channel, msg)
  }
}