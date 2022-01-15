package cn.tursom.im

import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Message

class SystemMsgSender(
  private val client: ImWebSocketClient,
  receiveMsg: TursomMsg.ImMsg,
) : MsgSender {
  override val sender: String = receiveMsg.chatMsg.sender
  override suspend fun send(msg: Message) {
    client.sendExtMsg(sender, msg)
  }
}