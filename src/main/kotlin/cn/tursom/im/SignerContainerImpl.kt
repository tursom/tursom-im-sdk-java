package cn.tursom.im

import cn.tursom.core.util.base64
import cn.tursom.core.util.toUTF8String

class SignerContainerImpl : SignerContainer {
  private val trustedSet = HashSet<String>()

  override fun addTrustedKey(key: ByteArray) {
    trustedSet.add(key.base64().toUTF8String())
  }

  override fun isTrusted(key: ByteArray): Boolean {
    return trustedSet.contains(key.base64().toUTF8String())
  }
}