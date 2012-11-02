package praktikum.beispiele.beispiel1

import jpcap.packet.TCPPacket

class StackWrapper2 extends Stack {

    QueueHelper helper = new QueueHelper()

    StackWrapper2(String confFileName, String environment) {
        super(confFileName, environment)
    }

    void processTCPPacket(TCPPacket packet) {
        helper.processTCPPacket(packet)
    }

    void processCommand(List cmd) {
        helper.processCommand(cmd)
    }
}
