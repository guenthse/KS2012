package praktikum.beispiele.beispiel1

import praktikum.beispiele.utils.Utils

class StackWrapper1 extends Stack {

    Helper helper = new Helper()

    StackWrapper1(String confFileName, String environment) {
        super(confFileName, environment)
    }

    void sendTCPPacket(long retransTimeout) {
        Utils.writeLog("StackWrapper1","sendTCPPacket", "${sendAckFlag ? "ACK, " : ""}${sendSynFlag ? "SYN, " : ""}${sendFinFlag ? "FIN, " : ""}${sendSeqNumber}, " +
                "${sendAckNumber}, \"${new String(sendData)}\"")
        helper.sendTCPPacket(sendAckFlag, sendSynFlag, sendFinFlag, sendSeqNumber, sendAckNumber, new String(sendData))
    }
}

