package praktikum.beispiele.beispiel1

import groovy.mock.interceptor.MockFor

import jpcap.packet.EthernetPacket
import jpcap.packet.TCPPacket
import org.junit.BeforeClass
import org.junit.Test
import praktikum.beispiele.utils.Cmd
import praktikum.beispiele.fsm.Event
import praktikum.beispiele.fsm.State
import praktikum.beispiele.utils.SendContainer
import praktikum.beispiele.utils.Utils

class TestAll extends GroovyTestCase {

    final int sec05 = 500
    final int sec1 = 1000
    final int sec2 = 2000
    final int sec3 = 3000
    final int sec4 = 4000

    String environment = "unikabel"
    String confFileName = "src/praktikum/beispiele/env.conf"

    // =================================================================

    @Test
    void testStateMaschine() {
        Stack worker = new Stack(confFileName, environment)

        Utils.writeLog("","testStateMaschine","== Start ==")
        assert State.S_SEND_SYN == worker.fsm.fire(Event.E_CONN_REQ)
        assert State.S_WAIT_SYN_ACK == worker.fsm.fire(Event.E_SEND_SYN)
        assert State.S_SEND_ACK_SYN_ACK == worker.fsm.fire(Event.E_RCVD_SYN_ACK)
        assert State.S_WAITING == worker.fsm.fire(Event.E_ACK_SYN_ACK_SENT)
    }

    // =================================================================

    @Test
    void testHandleStateChange() {

        long sendSeqNumber
        Stack worker
        def mock = new MockFor(Helper)

        Utils.writeLog("","testHandleStateChange","== Start ==")
        mock.use {

            worker = new StackWrapper1(confFileName, environment) as Stack

            // -------------------------------------------------------
            worker.recvSeqNumber = 100
            worker.recvAckNumber = 200
            worker.recvAckFlag = true
            worker.recvFinFlag = false
            worker.recvSynFlag = false
            worker.recvData = [] as byte[]


            // Verbindungsaufbau
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert !ack
                assert syn
                assert !fin
                assert !ackn
                assert !data
                sendSeqNumber = seqn
            }

            worker.fsm.setState(State.S_SEND_SYN)
            worker.handleStateChange(State.S_SEND_SYN)
            assert State.S_WAIT_SYN_ACK == worker.fsm.getState()

            // Verbindungsabbruch
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert fin
                assert !ackn
                assert !data
                sendSeqNumber = seqn
            }

            worker.fsm.setState(State.S_SEND_FIN)
            worker.handleStateChange(State.S_SEND_FIN)
            assert State.S_WAIT_FIN_ACK == worker.fsm.getState()
        }
    }

    // =================================================================

    @Test
    void testProcessTCPPacket() {

        long seqNumber, ackNumber
        boolean ackFlag, synFlag, finFlag
        Stack worker
        TCPPacket packet

        def mock = new MockFor(Helper)

        Utils.writeLog("","testProcessTCPPacket","== Start ==")
        mock.use {

            worker = new StackWrapper1(confFileName, environment) as Stack

            // -------------------------------------------------------

            // Receive SYN+ACK -----------------------------------------------

            worker.sendAckNumber = 0
            worker.sendSeqNumber = 100

            seqNumber = 200
            ackNumber = 101
            ackFlag = true
            synFlag = true
            finFlag = false

            worker.fsm.setCurrentState(State.S_WAIT_SYN_ACK)

            packet = new TCPPacket(80, 33333, seqNumber, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = []

            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack == true
                assert syn == false
                assert fin == false
                assert ackn == seqNumber + 1
                assert seqn == 101
                assert 0 == data.size()
            }

            worker.processTCPPacket(packet)

            assert State.S_WAITING == worker.fsm.getState()

            assert worker.recvAckNumber == ackNumber
            assert worker.recvSeqNumber == seqNumber

            seqNumber += 1

            assert worker.sendAckNumber == seqNumber
            assert worker.sendSeqNumber == ackNumber

            // Receive ACK+FIN -----------------------------------------

            worker.sendAckNumber = 210
            worker.sendSeqNumber = 100

            seqNumber = 210
            ackNumber = 101
            ackFlag = true
            synFlag = false
            finFlag = true

            worker.fsm.setCurrentState(State.S_WAIT_FIN_ACK)

            packet = new TCPPacket(80, 33333, seqNumber, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = []

            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack == true
                assert syn == false
                assert fin == false
                assert ackn == seqNumber + 1
                assert seqn == 101
                assert 0 == data.size()
            }

            worker.processTCPPacket(packet)

            assert State.S_IDLE == worker.fsm.getState()

            assert worker.recvAckNumber == ackNumber
            assert worker.recvSeqNumber == seqNumber

            seqNumber += 1

            assert worker.sendAckNumber == seqNumber
            assert worker.sendSeqNumber == ackNumber

            // Receive ACK ---------------------------------------------

            worker.sendAckNumber = 210
            worker.sendSeqNumber = 101

            seqNumber = 210
            ackNumber = 101
            ackFlag = true
            synFlag = false
            finFlag = false

            worker.fsm.setCurrentState(State.S_WAITING)

            packet = new TCPPacket(80, 33333, seqNumber, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = []

            worker.processTCPPacket(packet)

            assert State.S_WAITING == worker.fsm.getState()

            assert worker.recvAckNumber == ackNumber
            assert worker.recvSeqNumber == seqNumber

            assert worker.sendAckNumber == seqNumber
            assert worker.sendSeqNumber == ackNumber

            // Receive DATA --------------------------------------------

            worker.sendAckNumber = 210
            worker.sendSeqNumber = 101

            seqNumber = 210
            ackNumber = 101
            ackFlag = true
            synFlag = false
            finFlag = false

            worker.fsm.setCurrentState(State.S_WAITING)

            packet = new TCPPacket(80, 33333, seqNumber, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = "123" as byte[]

            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack == true
                assert syn == false
                assert fin == false
                assert ackn == seqNumber + packet.data.size()
                assert seqn == 101
                assert 0 == data.size()
            }

            worker.processTCPPacket(packet)

            assert State.S_WAITING == worker.fsm.getState()

            assert worker.recvAckNumber == ackNumber
            assert worker.recvSeqNumber == seqNumber

            seqNumber += packet.data.size()

            assert worker.sendAckNumber == seqNumber
            assert worker.sendSeqNumber == ackNumber
        }
    }

    // =================================================================

    @Test
    void testQueueHandler() {
        def mockSender = new MockFor(SenderHelper)
        def mock = new MockFor(QueueHelper)

        Utils.writeLog("","testQueueHandler","== Start ==")

        mockSender.use {
            mock.use {
                StackWrapper2 stack = new StackWrapper2(confFileName, environment)
                stack.sender = new SenderHelper()
                stack.start(false)

                // Sende-Queue
                TCPPacket packet = new TCPPacket(1, 2, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
                packet.data = []
                packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                        InetAddress.getByAddress([0, 0, 0, 0] as byte[]),
                        InetAddress.getByAddress([0, 0, 0, 0] as byte[]))
                EthernetPacket ether = new EthernetPacket()
                ether.frametype = EthernetPacket.ETHERTYPE_IP
                ether.src_mac = [0, 0, 0, 0, 0, 0]
                ether.dst_mac = [0, 0, 0, 0, 0, 0]
                packet.datalink = ether

                Utils.writeLog("","","-- Test1 --")
                // Ohne Sendewiederholung
                mockSender.demand.sendPacket(1) {p -> Utils.writeLog("Mock", "sendPacket", "")}
                stack.sendQueue.put(new SendContainer(packet, 0, 0))
                sleep(sec2)
                stack.sendQueue.clear()

                Utils.writeLog("","","-- Test2 --")
                // Mit Sendewiederholung
                mockSender.demand.sendPacket(2) {p -> Utils.writeLog("Mock", "sendPacket", "")}
                stack.sendQueue.put(new SendContainer(packet, 0, sec1))
                sleep(sec2)
                stack.sendQueue.clear()

                Utils.writeLog("","","-- Test3 --")
                // Sendewiederholung nach Retransmission Timeout testen
                mockSender.demand.sendPacket(3) {p -> Utils.writeLog("Mock", "sendPacket", "")}
                stack.sendQueue.put(new SendContainer(packet, 0, sec1))
                sleep(sec4)
                // "Quittieren" des TCP-Pakets
                stack.recvAckNumber = 1001
                // In dieser Zeit darf keine Sendewiederholung auftreten
                sleep(sec2)
                stack.sendQueue.clear()

                Utils.writeLog("","","-- Test4 --")
                // Alle Queues füllen
                mock.demand.processCommand(1) {cmd -> Utils.writeLog("Mock","processCommand", "${cmd}")}
                mock.demand.processTCPPacket(1) {p -> Utils.writeLog("Mock","processTCPPacket","")}
                mockSender.demand.sendPacket(2) {p -> Utils.writeLog("Mock","sendPacket","")}
                stack.cmdQueue.put(["c1"])
                sleep(sec05)
                stack.recvTCPQueue.put(packet)
                sleep(sec05)
                stack.recvAckNumber = 1000
                stack.sendQueue.put(new SendContainer(packet, 0, sec1))
                // Warten auf Sendewiederholung
                sleep(sec2)
                stack.sendQueue.clear()

                Utils.writeLog("","","-- Test5 --")
                // Mehrere zu sendende Pakete
                mockSender.demand.sendPacket(4) {p -> Utils.writeLog("Mock", "sendPacket","")}
                mock.demand.processCommand(1) {cmd -> Utils.writeLog("Mock", "processCommand","${cmd}")}
                mock.demand.processTCPPacket(1) {p -> Utils.writeLog("Mock", "processTCPPacket", "")}
                stack.sendQueue.put(new SendContainer(packet, 0, 0))
                stack.sendQueue.put(new SendContainer(packet, 0, 0))
                stack.sendQueue.put(new SendContainer(packet, 0, 0))
                stack.sendQueue.put(new SendContainer(packet, 0, 0))
                stack.cmdQueue.put(["c2"])
                sleep(sec2)
                stack.recvTCPQueue.put(packet)
                // Warten bis alle Queues geleert sind
                sleep(sec2)

                stack.stop()
            }
        }
    }

    // =================================================================

    @Test
    void testTransTimeout() {
        def mockSender = new MockFor(SenderHelper)

        Utils.writeLog("","testTransTimeout","== Start ==")

        mockSender.demand.sendPacket(3) {packet -> Utils.writeLog("Mock","sendPacket","${System.currentTimeMillis()}") }

        mockSender.use {
            StackWrapper2 stack = new StackWrapper2(confFileName, environment)
            stack.sender = new SenderHelper()
            stack.start(false)

            // Sende-Queue
            TCPPacket packet = new TCPPacket(1, 2, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
            packet.data = []
            packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                    InetAddress.getByAddress([0, 0, 0, 0] as byte[]),
                    InetAddress.getByAddress([0, 0, 0, 0] as byte[]))
            EthernetPacket ether = new EthernetPacket()
            ether.frametype = EthernetPacket.ETHERTYPE_IP
            ether.src_mac = [0, 0, 0, 0, 0, 0]
            ether.dst_mac = [0, 0, 0, 0, 0, 0]
            packet.datalink = ether

            stack.sendQueue.put(new SendContainer(packet, 0, sec1))
            // das Paket wird 3 x gesendet
            sleep(sec4)
            stack.stop()
        }
    }

    // =================================================================

    @Test
    void testSequenz() {

        long ackNumber = 0
        boolean ackFlag
        boolean synFlag
        boolean finFlag

        TCPPacket packet, packet2, packet3, packet4, packet5

        def mock = new MockFor(Helper)

        Utils.writeLog("","testSequenz","== Start ==")

        mock.use {

            Stack worker = new StackWrapper1(confFileName, environment) as Stack
            worker.start(false)

            /*
                    TCPPacket packet = new TCPPacket(sendSrcPort, sendDstPort, sendSeqNumber, sendAckNumber, false, sendAckFlag, false,
                            false, sendSynFlag, sendFinFlag, true, true, sendWindowSize, 0)
            */

            // Öffnen der Verbindung ---------------------------

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert !ack
                assert syn
                assert !fin
                assert !ackn
                assert !data
                ackNumber = seqn + 1
            }

            worker.cmdQueue.put([Cmd.OPEN])
            sleep(sec1)

            // mit SYN+ACK antworten -----------------------------------
            ackFlag = true
            synFlag = true
            finFlag = false

            // Senden:
            packet = new TCPPacket(80, 33333, 200, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = []

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert !data
                assert ackn == 201
                assert seqn == ackNumber
            }

            worker.recvTCPQueue.put(packet)
            sleep(sec1)

            assert worker.fsm.currentState == State.S_WAITING

            // Request senden --------------------------------------

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert data
                assert ackn == 201
                assert seqn == ackNumber
                ackNumber += data.size()
            }

            byte[] applicationData = """GET / HTTP/1.1\n\n""".getBytes()

            // Übergabe des Kommandos und der HTTP-PDU
            worker.cmdQueue.put([Cmd.GET, applicationData])
            sleep(sec1)

            assert worker.fsm.currentState == State.S_WAITING

            // Senden 1:
            ackFlag = true
            synFlag = false
            finFlag = false

            packet = new TCPPacket(80, 33333, 201, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = "123" as byte[]

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert ackn == 204
                assert seqn == ackNumber
                ackNumber += data.size()
            }

            worker.recvTCPQueue.put(packet)
            sleep(sec1)

            // Senden 2:
            ackFlag = true
            synFlag = false
            finFlag = false

            packet2 = new TCPPacket(80, 33333, 204, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet2.data = "456" as byte[]

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert ackn == 207
                assert seqn == ackNumber
                ackNumber += data.size()
            }

            packet3 = new TCPPacket(80, 33333, 207, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet3.data = "789" as byte[]

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert ackn == 210
                assert seqn == ackNumber
                ackNumber += data.size()
            }

            packet4 = new TCPPacket(80, 33333, 210, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet4.data = "abc" as byte[]

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert ackn == 213
                assert seqn == ackNumber
                ackNumber += data.size()
            }

            packet5 = new TCPPacket(80, 33333, 213, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet5.data = "def" as byte[]

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert ackn == 216
                assert seqn == ackNumber
                ackNumber += data.size()
            }

            worker.recvTCPQueue.put(packet2)
            worker.recvTCPQueue.put(packet3)
            worker.recvTCPQueue.put(packet4)
            worker.recvTCPQueue.put(packet5)
            sleep(sec2)

            // Verbindung abbauen ---------------------------------

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert fin
                assert ackn
                assert !data
                ackNumber = seqn + 1
            }

            worker.cmdQueue.put([Cmd.CLOSE])
            sleep(sec1)
            assert State.S_WAIT_FIN_ACK == worker.fsm.getCurrentState()


            // mit FIN+ACK antworten -----------------------------------
            ackFlag = true
            synFlag = false
            finFlag = true

            // Senden:
            packet = new TCPPacket(80, 33333, 210, ackNumber, false, ackFlag, false,
                    false, synFlag, finFlag, true, true, 65535, 0)
            packet.data = []

            // Erwarten:
            mock.demand.sendTCPPacket(1) {ack, syn, fin, seqn, ackn, data ->
                assert ack
                assert !syn
                assert !fin
                assert !data
                assert ackn == 211
                assert seqn == ackNumber
            }

            worker.recvTCPQueue.put(packet)
            sleep(sec1)

            assert worker.fsm.currentState == State.S_IDLE

            worker.stop()
        }
    }

    // =================================================================

    @Test
    void testReceivePacket() {

        Utils.writeLog("","testReceivePacket","== Start ==")

        Stack worker = new Stack(confFileName, environment)
        // processQueues stoppen
        worker.stopThreads = true
        sleep(sec1)

        TCPPacket packet
        EthernetPacket ether

        int destPort = 1234
        worker.ownPort = destPort

        String destIpAddress = "1.2.3.4"
        List destIPAddressList = [1,2,3,4]
        worker.ownIPAddress = destIpAddress

        String destMacAddress = "11:22:33:44:55:66"
        List destMacAddressList = [0x11,0x22,0x33,0x44,0x55,0x66]
        worker.ownMacAddress = destMacAddress

        // Alle Angaben richtig
        packet = new TCPPacket(1, destPort, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
        packet.data = []
        packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                InetAddress.getByAddress([1, 1, 1, 1] as byte[]),
                InetAddress.getByAddress(destIPAddressList as byte[]))
        ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_IP
        ether.src_mac = [0, 0, 0, 0, 0, 0]
        ether.dst_mac = destMacAddressList
        packet.datalink = ether

        worker.receivePacket(packet)
        sleep(sec1)
        assert 1 == worker.recvTCPQueue.size()
        worker.recvTCPQueue.clear()

        // Falsche MAC-Adresse

        packet = new TCPPacket(1, destPort, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
        packet.data = []
        packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                InetAddress.getByAddress([1, 1, 1, 1] as byte[]),
                InetAddress.getByAddress(destIPAddressList as byte[]))
        ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_IP
        ether.src_mac = [0, 0, 0, 0, 0, 0]
        ether.dst_mac = [0, 0, 0, 0, 0, 0]
        packet.datalink = ether

        worker.receivePacket(packet)
        sleep(sec1)
        assert 0 == worker.recvTCPQueue.size()
        worker.recvTCPQueue.clear()

        // Falsches Ethernet-Protokoll

        packet = new TCPPacket(1, destPort, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
        packet.data = []
        packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                InetAddress.getByAddress([1, 1, 1, 1] as byte[]),
                InetAddress.getByAddress(destIPAddressList as byte[]))
        ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_ARP
        ether.src_mac = [0, 0, 0, 0, 0, 0]
        ether.dst_mac = destMacAddressList
        packet.datalink = ether

        worker.receivePacket(packet)
        sleep(sec1)
        assert 0 == worker.recvTCPQueue.size()
        worker.recvTCPQueue.clear()

        // Falsche IP-Adresse

        packet = new TCPPacket(1, destPort, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
        packet.data = []
        packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                InetAddress.getByAddress([1, 1, 1, 1] as byte[]),
                InetAddress.getByAddress([2, 2, 2, 2] as byte[]))
        ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_IP
        ether.src_mac = [0, 0, 0, 0, 0, 0]
        ether.dst_mac = destMacAddressList
        packet.datalink = ether

        worker.receivePacket(packet)
        sleep(sec1)
        assert 0 == worker.recvTCPQueue.size()
        worker.recvTCPQueue.clear()

        // Falsches Transport-Protokoll

        packet = new TCPPacket(1, destPort, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
        packet.data = []
        packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 7,
                InetAddress.getByAddress([1, 1, 1, 1] as byte[]),
                InetAddress.getByAddress(destIPAddressList as byte[]))
        ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_IP
        ether.src_mac = [0, 0, 0, 0, 0, 0]
        ether.dst_mac = destMacAddressList
        packet.datalink = ether

        worker.receivePacket(packet)
        sleep(sec1)
        assert 0 == worker.recvTCPQueue.size()
        worker.recvTCPQueue.clear()

        // Falscher Zielport
        destPort = 2

        packet = new TCPPacket(1, destPort, 1000, 2000, false, false, false, false, false, false, false, false, 0, 0)
        packet.data = []
        packet.setIPv4Parameter(0, false, false, false, 0, false, false, false, 0, 1, 20, 6,
                InetAddress.getByAddress([1, 1, 1, 1] as byte[]),
                InetAddress.getByAddress(destIPAddressList as byte[]))
        ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_IP
        ether.src_mac = [0, 0, 0, 0, 0, 0]
        ether.dst_mac = destMacAddressList
        packet.datalink = ether

        worker.receivePacket(packet)
        sleep(sec1)
        assert 0 == worker.recvTCPQueue.size()
        worker.recvTCPQueue.clear()
    }
}
