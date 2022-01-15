package cn.tursom.im

import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Message

interface MsgSender : suspend (Message) -> Unit {
  val client: ImWebSocketClient
  val receiveMsg: TursomMsg.ImMsg
  val sender: String
  val channel: Int get() = -1

  suspend fun send(msg: Message)
  override suspend fun invoke(msg: Message) = send(msg)
}
