package cn.tursom.im

import cn.tursom.core.encrypt.ECC
import cn.tursom.core.notifyAll
import cn.tursom.core.wait
import cn.tursom.im.protobuf.TursomMsg
import cn.tursom.im.protobuf.TursomSystemMsg
import cn.tursom.log.impl.Slf4jImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import org.junit.Test

@OptIn(DelicateCoroutinesApi::class)
class ImConnectTest {
  companion object : Slf4jImpl() {
    // private const val url = "ws://127.0.0.1:12345/ws"
    // private const val token = "CM+s/Pn1waaxeBILMjNpbWlnblJzRVU="
    private const val url = "wss://im.tursom.cn/ws"
    private const val token = "CNmX3Zb/m+HAYRILMjF0c2ZkMXJRNU4="
  }

  object TestHandler {
    val signer = ECC()
    suspend fun handleGetLiveDanmuRecordList(
      msg: TursomSystemMsg.GetLiveDanmuRecordList,
      msgSender: MsgSender,
    ) {
      msgSender.send(
        TursomSystemMsg.ReturnLiveDanmuRecordList.newBuilder()
          .setReqId(msg.reqId)
          .addRecordList(
            TursomSystemMsg.LiveDanmuRecord.newBuilder()
              .setId("test id")
              .setStart(1)
              .setStop(10)
          )
          .addRecordList(
            TursomSystemMsg.LiveDanmuRecord.newBuilder()
              .setId("test id 2")
              .setStart(11)
              .setStop(20)
          )
          .toEncryptedMsg(signer)
      )
    }
  }

  private fun newClient(
    onLogin: (suspend (client: ImWebSocketClient, receiveMsg: TursomMsg.ImMsg) -> Unit)? = null,
  ) = connect(url, token, onLogin)

  @Test
  fun test() {
    val imWebSocketClient = ImWebSocketClient(url, token)
    val webSocketHandler = imWebSocketClient.handler

    val handler = connect(url, token).handler
    handler.system.registerHandlerObject(TestHandler)
    handler.broadcast.registerHandlerObject(TestHandler)

    imWebSocketClient.addTrustedKey(TestHandler.signer.publicKeyEncoded)
    imWebSocketClient.addDecoder("ECC", TestHandler.signer.publicKeyEncoded, TestHandler.signer)
    imWebSocketClient.addDecoder("EC", TestHandler.signer.publicKeyEncoded, TestHandler.signer)
    webSocketHandler.registerMsgHandle(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
      logger.debug("login {}", if (receiveMsg.loginResult.success) "success" else "failed")
      if (!receiveMsg.loginResult.success) return@registerMsgHandle

      logger.debug(
        "get live danmu record list: {}", client.callGetLiveDanmuRecordList(
          client.currentUserId!!,
          "test room"
        )
      )

      logger.debug("current id: {}", client.imSnowflake.nodeId)

      client.close()
    }

    imWebSocketClient.open()
    imWebSocketClient.waitClose()
  }

  @Test
  fun testBroadcast() {
    val imWebSocketClient = ImWebSocketClient(url, token)
    val broadcastListenClient = ImWebSocketClient(url, token)

    broadcastListenClient.handler.registerMsgHandle(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
      if (!receiveMsg.loginResult.success) {
        broadcastListenClient.close()
        imWebSocketClient.close()
        return@registerMsgHandle
      }

      client.handler.broadcast.registerHandler(TursomMsg.MsgContent.ContentCase.MSG) {
        imWebSocketClient.close()
        client.close()
      }

      client.listenBroadcast(123)
      imWebSocketClient.open()
    }

    imWebSocketClient.handler.registerMsgHandle(TursomMsg.ImMsg.ContentCase.LOGINRESULT) { client, receiveMsg ->
      if (!receiveMsg.loginResult.success) {
        broadcastListenClient.close()
        imWebSocketClient.close()
        return@registerMsgHandle
      }

      client.sendBroadcast(123, "test broadcast")
    }

    broadcastListenClient.open()?.await()
    broadcastListenClient.waitClose()
    imWebSocketClient.waitClose()
  }
}