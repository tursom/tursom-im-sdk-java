package cn.tursom.im

import com.google.protobuf.Message

interface MsgSender : suspend (Message) -> Unit {
  val sender: String
  val channel: Int get() = -1

  suspend fun send(msg: Message)
  override suspend fun invoke(msg: Message) = send(msg)
}
