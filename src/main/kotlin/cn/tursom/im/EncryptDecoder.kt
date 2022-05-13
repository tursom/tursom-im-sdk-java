package cn.tursom.im

import cn.tursom.core.encrypt.Encrypt
import cn.tursom.im.protobuf.TursomMsg.EncryptMsg

interface EncryptDecoder {
  fun addDecoder(algorithm: String, key: ByteArray, encrypt: Encrypt)

  fun decode(msg: EncryptMsg): ByteArray?
}
