package cn.tursom.im

import cn.tursom.im.protobuf.TursomSystemMsg
import com.google.protobuf.Message

interface TursomSystemMsgHandlerRegister {
  fun <T : Message> registerHandler(clazz: Class<T>, handler: suspend (unpackMsg: T, msgSender: MsgSender) -> Unit)
}

inline fun <reified T : Message> TursomSystemMsgHandlerRegister.registerHandler(
  noinline handler: suspend (
    unpackMsg: T,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(T::class.java, handler)

fun TursomSystemMsgHandlerRegister.registerListenLiveRoomHandler(
  handler: suspend (
    listenLiveRoom: TursomSystemMsg.ListenLiveRoom,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(handler)

fun TursomSystemMsgHandlerRegister.registerAddMailReceiverHandler(
  handler: suspend (
    listenLiveRoom: TursomSystemMsg.AddMailReceiver,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(handler)

fun TursomSystemMsgHandlerRegister.registerGetLiveDanmuRecordListHandler(
  handler: suspend (
    listenLiveRoom: TursomSystemMsg.GetLiveDanmuRecordList,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(handler)

fun TursomSystemMsgHandlerRegister.registerReturnLiveDanmuRecordListHandler(
  handler: suspend (
    listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecordList,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(handler)

fun TursomSystemMsgHandlerRegister.registerGetLiveDanmuRecordHandler(
  handler: suspend (
    listenLiveRoom: TursomSystemMsg.GetLiveDanmuRecord,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(handler)

fun TursomSystemMsgHandlerRegister.registerReturnLiveDanmuRecordHandler(
  handler: suspend (
    listenLiveRoom: TursomSystemMsg.ReturnLiveDanmuRecord,
    msgSender: MsgSender,
  ) -> Unit,
) = registerHandler(handler)
