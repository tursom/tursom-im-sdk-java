package cn.tursom.im

import cn.tursom.core.encrypt.PublicKeyEncrypt
import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Message

class SystemMsgSender(
  override val client: ImWebSocketClient,
  override val receiveMsg: TursomMsg.ImMsg,
) : MsgSender {
  override val sender: String = receiveMsg.chatMsg.sender
  override val content: TursomMsg.MsgContent = receiveMsg.chatMsg.content

  override suspend fun send(msg: Message) {
    when (msg) {
      is TursomMsg.SignedMsg -> client.sendSignedMsg(sender, msg)
      is TursomMsg.EncryptMsg -> client.sendEncryptMsg(sender, msg)
      else -> client.sendExtMsg(sender, msg)
    }
  }
}