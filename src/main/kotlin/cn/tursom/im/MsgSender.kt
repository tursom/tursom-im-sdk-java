package cn.tursom.im

import cn.tursom.core.encrypt.PublicKeyEncrypt
import cn.tursom.im.protobuf.TursomMsg
import com.google.protobuf.Message

interface MsgSender : suspend (Message) -> Unit {
  val client: ImWebSocketClient
  val receiveMsg: TursomMsg.ImMsg
  val sender: String

  /**
   * get broad cast channel for receive msg
   */
  val channel: Int get() = -1

  val content: TursomMsg.MsgContent
  val reqId: String get() = content.reqId
  val signed: Boolean
    get() = content.contentCase == TursomMsg.MsgContent.ContentCase.SIGNED ||
      content.contentCase == TursomMsg.MsgContent.ContentCase.ENCRYPT
  val signedMsg get() = content.signed

  suspend fun send(msg: Message)
  suspend fun send(msg: Message.Builder) = send(msg.build())


  suspend fun send(
    msg: Message,
    singer: PublicKeyEncrypt,
    digest: String = "SHA256",
    algorithm: String = singer.algorithm,
  ) = send(msg.toSingedMsg(singer))

  suspend fun send(
    msg: Message.Builder,
    singer: PublicKeyEncrypt,
    digest: String = "SHA256",
    algorithm: String = singer.algorithm,
  ) = send(msg.build(), singer)

  override suspend fun invoke(msg: Message) = send(msg)
}
