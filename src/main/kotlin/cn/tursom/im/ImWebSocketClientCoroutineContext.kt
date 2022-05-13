package cn.tursom.im

import kotlin.coroutines.CoroutineContext

class ImWebSocketClientCoroutineContext(
  val client: ImWebSocketClient,
) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<ImWebSocketClientCoroutineContext>

  override val key: CoroutineContext.Key<*> get() = Key
}
