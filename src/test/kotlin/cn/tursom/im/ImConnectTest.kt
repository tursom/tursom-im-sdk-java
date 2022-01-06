package cn.tursom.im

import cn.tursom.core.notifyAll
import cn.tursom.core.wait
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import org.junit.Test

@OptIn(DelicateCoroutinesApi::class)
class ImConnectTest {
  companion object : Slf4jImpl()

  @Test
  fun test() {
//    val imWebSocketClient = connect(
//      "ws://127.0.0.1:12345/ws",
//      "CNmX3Zb/m+HAYRILMjF0c2ZkMXJRNU4=",
//      open = false
//    )
    val imWebSocketClient = connect(
      "wss://im.tursom.cn/ws",
      "CNmX3Zb/m+HAYRILMjF0c2ZkMXJRNU4=",
    ) { client, receiveMsg ->
    }
    val webSocketHandler = imWebSocketClient.handler
    val tursomSystemMsgHandler = webSocketHandler.system

    tursomSystemMsgHandler.registerGetLiveDanmuRecordListHandler { client, receiveMsg, listenLiveRoom ->
      client.sendExtMsg(
        receiveMsg.chatMsg.sender,
        TursomSystemMsg.ReturnLiveDanmuRecordList.newBuilder()
          .setReqId(listenLiveRoom.reqId)
          .addRecordList(TursomSystemMsg.LiveDanmuRecord.newBuilder()
            .setId("test id")
            .setStart(1)
            .setStop(10))
          .addRecordList(TursomSystemMsg.LiveDanmuRecord.newBuilder()
            .setId("test id 2")
            .setStart(11)
            .setStop(20))
          .build()
      )
    }

//    webSocketHandler.handleMsg(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
//      logger.debug("login {}", if (receiveMsg.loginResult.success) "success" else "failed")
//      if (!receiveMsg.loginResult.success) return@handleMsg
//
//      logger.debug("get live danmu record list: {}", client.callGetLiveDanmuRecordList(
//        tursomSystemMsgHandler,
//        client.currentUserId!!,
//        "test room"
//      ))
//
//      logger.debug("current id: {}", client.imSnowflake.nodeId)
//
//      client.close()
//    }

    webSocketHandler.onClose {
      webSocketHandler.notifyAll { }
    }
    imWebSocketClient.open()
    webSocketHandler.wait { }
  }

  //@Test
  //fun testBroadcast() {
  //  val imWebSocketClient = connect(
  //    "ws://127.0.0.1:12345/ws",
  //    "CNeb25i9srXUchILMjFiNjg2YUIzejY=",
  //    open = false
  //  )
  //  val broadcastListenClient = connect(
  //    "ws://127.0.0.1:12345/ws",
  //    "CNeb25i9srXUchILMjFiNjg2YUIzejY=",
  //    open = false
  //  )
  //
  //  imWebSocketClient.handler.handleMsg(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
  //    if (!receiveMsg.loginResult.success) {
  //      client.close()
  //      return@handleMsg
  //    }
  //
  //    client.sendBroadcast(123, "test broadcast")
  //  }
  //
  //  broadcastListenClient.handler.handleMsg(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
  //    client.handler.broadcast.handle(TursomMsg.MsgContent.ContentCase.MSG) { _, _ ->
  //      imWebSocketClient.close()
  //      client.close()
  //    }
  //
  //    client.listenBroadcast(123)
  //
  //    imWebSocketClient.open()
  //  }
  //
  //  broadcastListenClient.open()
  //
  //  imWebSocketClient.waitClose()
  //}
}