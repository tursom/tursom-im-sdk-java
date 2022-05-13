package cn.tursom.im.ext

import cn.tursom.im.MsgSender
import cn.tursom.im.TursomSystemMsgHandler
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.im.registerReturnLiveDanmuRecordListHandler
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

object LiveDanmuRecordListHandler {
  private val key = TursomSystemMsgHandler.ctxEnv.newKey<Cache<String,
    suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
      msgSender: MsgSender,
    ) -> Unit>>().withSynchronizedDefault {
    Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build()
  }

  fun register(handler: TursomSystemMsgHandler) {
    val tempHandler = handler.ctx[key]

    handler.registerReturnLiveDanmuRecordListHandler { listenLiveRoom, msgSender ->
      tempHandler.getIfPresent(listenLiveRoom.reqId)?.invoke(listenLiveRoom, msgSender)
    }
  }

  /**
   * register temp [handler] for [reqId] on [msgHandler]
   *
   * [handler] will expired after 1 minute
   */
  fun tempHandle(
    msgHandler: TursomSystemMsgHandler,
    reqId: String,
    handler: suspend (
      listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
      msgSender: MsgSender,
    ) -> Unit,
  ) {
    msgHandler.ctx[key].put(reqId, handler)
  }
}