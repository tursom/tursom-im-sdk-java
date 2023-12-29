package cn.tursom.im

import cn.tursom.core.util.base64
import cn.tursom.core.encrypt.Encrypt
import cn.tursom.core.util.toUTF8String
import cn.tursom.core.util.toUTF8String
import cn.tursom.im.protobuf.TursomMsg

class EncryptDecoderImpl : EncryptDecoder {
  private val keyMap = HashMap<String, Encrypt>()

  override fun addDecoder(algorithm: String, key: ByteArray, encrypt: Encrypt) {
    keyMap[algorithm + key.base64().toUTF8String()] = encrypt
  }

  override fun decode(msg: TursomMsg.EncryptMsg): ByteArray? {
    val key = msg.algorithm + msg.publicKey.toByteArray().base64().toUTF8String()
    val encrypt = keyMap[key] ?: return null
    val data = msg.msg.toByteArray()
    return encrypt.decrypt(data)
  }
}