package cn.tursom.im

import cn.tursom.core.*
import cn.tursom.core.encrypt.*
import cn.tursom.core.reflect.MethodInspector
import cn.tursom.im.exception.LoginFailedException
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomMsg.EncryptMsg
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import java.util.concurrent.CountDownLatch

internal val defaultSnowflake = Snowflake(0)

fun connect(
  url: String,
  token: String,
  onLogin: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)? = null,
): ImWebSocketClient {
  val webSocketClient = ImWebSocketClient(url, token)
  if (onLogin != null) {
    webSocketClient.handler.registerMsgHandle(TursomMsg.ImMsg.ContentCase.LOGINRESULT, onLogin)
  }
  webSocketClient.open()
  return webSocketClient
}

fun connectAndWait(
  url: String,
  token: String,
  onLogin: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)? = null,
): ImWebSocketClient {
  var exception: Throwable? = null
  val cdl = CountDownLatch(1)
  val webSocketClient = ImWebSocketClient(url, token)
  webSocketClient.handler.registerMsgHandle(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
    if (receiveMsg.loginResult.success) {
      if (onLogin != null) try {
        onLogin(client, receiveMsg)
      } catch (e: Throwable) {
        exception = e
      }
    } else {
      exception = LoginFailedException("login failed")
    }
    cdl.countDown()
  }
  webSocketClient.open()

  cdl.await()
  exception?.throws()
  return webSocketClient
}

fun TursomMsg.SignedMsg.check(signerContainer: SignerContainer): Boolean {
  val publicKey = publicKey.toByteArray()
  return when (algorithm) {
    "RSA" -> {
      signerContainer.isTrusted(publicKey) && RSA(publicKey).verify(msg.toByteArray(), sign.toByteArray(), realDigest)
    }
    "EC", "ECC" -> {
      signerContainer.isTrusted(publicKey) && ECC(publicKey).verify(msg.toByteArray(), sign.toByteArray(), realDigest)
    }
    else -> throw UnsupportedOperationException()
  }
}

private val TursomMsg.SignedMsg.realDigest
  get() = digest.ifEmpty { "SHA256" }


/**
 * 解析对象的所有方法，并将该方法注册为处理方法
 * 方法签名为:
 * suspend fun 方法名(
 *     msg: T,
 *     msgSender: MsgSender,
 * )
 * T 为 Message 的子类
 */
fun TursomSystemMsgHandler.registerHandlerObject(handler: Any) {
  MethodInspector.forEachSuspendMethod(
    handler,
    Unit::class.java,
    Message::class.java,
    MsgSender::class.java,
  ) { method, handlerCallback ->
    registerHandler(method.parameterTypes[method.parameterTypes.size - 3].uncheckedCast(), handlerCallback)
  }
}

fun ByteArray.toSingedMsg(
  singer: PublicKeyEncrypt,
  digest: String = "SHA256",
  algorithm: String = singer.algorithm,
): TursomMsg.SignedMsg {
  val sign = singer.sign(this, digest)

  return TursomMsg.SignedMsg.newBuilder()
    .setMsg(ByteString.copyFrom(this))
    .setSign(ByteString.copyFrom(sign))
    .setPublicKey(ByteString.copyFrom(singer.publicKey!!.encoded))
    .setAlgorithm(algorithm)
    .build()
}

fun Message.toSingedMsg(
  singer: PublicKeyEncrypt,
  digest: String = "SHA256",
  algorithm: String = singer.algorithm,
): TursomMsg.SignedMsg = com.google.protobuf.Any.pack(this).toByteArray().toSingedMsg(singer, digest, algorithm)

fun Message.Builder.toSingedMsg(
  singer: PublicKeyEncrypt,
  digest: String = "SHA256",
  algorithm: String = singer.algorithm,
) = build().toSingedMsg(singer, digest, algorithm)

fun Message.toEncryptedMsg(
  singer: PublicKeyEncrypt,
  algorithm: String = singer.algorithm,
) = EncryptMsg.newBuilder()
  .setMsg(ByteString.copyFrom(singer.encrypt(com.google.protobuf.Any.pack(this).toByteArray())))
  .setAlgorithm(algorithm)
  .setPublicKey(ByteString.copyFrom(singer.publicKey!!.encoded))
  .build()!!

fun Message.Builder.toEncryptedMsg(
  singer: PublicKeyEncrypt,
  algorithm: String = singer.algorithm,
) = build().toEncryptedMsg(singer, algorithm)
