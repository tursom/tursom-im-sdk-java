package cn.tursom.im

interface SignerContainer {
  fun addTrustedKey(key: ByteArray)
  fun isTrusted(key: ByteArray): Boolean
}
