package cn.tursom.im

import cn.tursom.core.isInheritanceFrom
import cn.tursom.core.isStatic
import cn.tursom.core.uncheckedCast
import com.google.protobuf.Message
import java.util.concurrent.ConcurrentHashMap

object AnyUtils {
  private val cache = ConcurrentHashMap<Class<out Message>, String?>()
  fun getTypeUrl(clazz: Class<out Message>): String {
    return cache.getOrPut(clazz) {
      try {
        val getDefaultInstance = clazz.getMethod("getDefaultInstance")
        if (getDefaultInstance.isStatic() &&
          getDefaultInstance.returnType.isInheritanceFrom(Message::class.java)
        ) {
          val defaultInstance = getDefaultInstance.invoke(null).uncheckedCast<Message>()
          return com.google.protobuf.Any.pack(defaultInstance, "cn.tursom").typeUrl
        }
      } catch (_: Exception) {
      }
      null
    }!!
  }
}