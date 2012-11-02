package praktikum.beispiele.beispiel1


//========================================================================================================//
// Importe ANFANG
//========================================================================================================//

import jpcap.PacketReceiver
import praktikum.beispiele.fsm.Event
import praktikum.beispiele.fsm.State
import jpcap.packet.IPPacket
import jpcap.NetworkInterface
import jpcap.JpcapCaptor
import jpcap.packet.EthernetPacket
import jpcap.packet.TCPPacket
import jpcap.packet.Packet
import java.util.concurrent.LinkedBlockingQueue
import praktikum.beispiele.utils.SendContainer
import java.util.concurrent.DelayQueue
import praktikum.beispiele.fsm.FiniteStateMachine
import praktikum.beispiele.utils.Utils
import praktikum.beispiele.utils.Cmd

//========================================================================================================//
// Importe ENDE
//========================================================================================================//


//========================================================================================================//
// Stack-Klasse ANFANG
//========================================================================================================//

/**
 * Der Netzwerkstack.
 * <p/>
 * Beinhaltet Fragmente der Protokolle HTTP, TCP, IP und IEEE 802.3 ("Ethernet-MAC").<br/>
 * HTTP - Hyper Text Transfer Protocol, TCP - Transport Control Protocol, IP - Internet Protocol,
 * IEEE - Institut of Electrical and Electronics Engineers, Ethernet - Produktbezeichnung eines häufig verwendeten LAN,
 * LAN - Local Area Network, MAC - Medium Access Control
 * <p/>
 * Für weitere Erläuterungen siehe die Kommentare im Quelltext.
 * <p/>
 * Implementiert das Interface PacketReceiver, so das bei jedem empfangenen Ethernet-Frame die Methode
 * "{@link praktikum.beispiele.beispiel1.Stack#receivePacket(jpcap.packet.Packet) receivePacket}" gerufen wird
 */
public class Stack implements PacketReceiver {

    //========================================================================================================//
    // EINSTELLUNGEN ANFANG
    //========================================================================================================//

    // Diese Werte werden im Konstruktor aus der Konfigurationsdatei "env.conf" gelesen und eingestellt:

    /** Eigene IPv4-Adresse<br/>
     */
    String ownIPAddress

    /** Eigene MAC-Adresse*/
    String ownMacAddress

    /** IP-Adresse des Web-Servers */
    String httpServerIPAddress

    /** MAC-Adresse des nächsten zu adressierenden Geräts (entweder ein Router
     *  oder der Server) */
    String destinationMacAddress
    //----------------------------------------------------------

    //========================================================================================================//
    // EINSTELLUNGEN ENDE
    //========================================================================================================//


    //========================================================================================================//
    // TCP ANFANG
    //========================================================================================================//

    // Finite State Machine

    /**
     *  Beschreibung der TCP-Zustandsübergänge für dieses Programmbeispiel, d.h. unvollständig!<br/>
     *  Der Anfangszustand ist "S_IDLE".<br/>
     *  Hier müssen Sie ändern und ergänzen.
     */
    List<Map> transitions =
        [
                // Verbindungsaufbau
                [on: Event.E_CONN_REQ, from: State.S_IDLE, to: State.S_SEND_SYN],
                [on: Event.E_SEND_SYN, from: State.S_SEND_SYN, to: State.S_WAIT_SYN_ACK],
                [on: Event.E_RCVD_SYN_ACK, from: State.S_WAIT_SYN_ACK, to: State.S_SEND_ACK_SYN_ACK],
                [on: Event.E_ACK_SYN_ACK_SENT, from: State.S_SEND_ACK_SYN_ACK, to: State.S_WAITING],
                // Datenübertragung
                [on: Event.E_SEND_DATA, from: State.S_WAITING, to: State.S_SEND_DATA],
                [on: Event.E_DATA_SENT, from: State.S_SEND_DATA, to: State.S_WAITING],
                [on: Event.E_RCVD_DATA, from: State.S_WAITING, to: State.S_RCVD_DATA],
                [on: Event.E_RCVD_ACK, from: State.S_WAITING, to: State.S_RCVD_ACK],
                [on: Event.E_WAITING, from: State.S_RCVD_DATA, to: State.S_WAITING],
                [on: Event.E_WAITING, from: State.S_RCVD_ACK, to: State.S_WAITING],
                // Verbindungsabbau
                [on: Event.E_DISCONN_REQ, from: State.S_WAITING, to: State.S_SEND_FIN],
                [on: Event.E_SEND_FIN, from: State.S_SEND_FIN, to: State.S_WAIT_FIN_ACK],
                [on: Event.E_RCVD_FIN_ACK, from: State.S_WAIT_FIN_ACK, to: State.S_SEND_ACK_FIN_ACK],
                [on: Event.E_ACK_FIN_ACK_SENT, from: State.S_SEND_ACK_FIN_ACK, to: State.S_IDLE],
        ]

    // Verschiedene protokollrelevante Parameter
    // zu sendende Parameter
    /** Portnummer des Absenders */
    int sendSrcPort = 33333
    /** Eigene Portnummer */
    int ownPort = sendSrcPort
    /** Portnummer des Empfängers */
    int sendDstPort = 80
    /** Größe des Empfangsfensters */
    int sendWindowSize = 100
    /** Zu sendende Sequenznummer */
    long sendSeqNumber = 0
    /** Zu sendende Quittierungsnummer */
    long sendAckNumber = 0
    /** ACK-Flag*/
    boolean sendAckFlag = false
    /** SYN-Flag*/
    boolean sendSynFlag = false
    /** FIN-Flag*/
    boolean sendFinFlag = false
    /** Zu sendende Anwendungsdaten */
    byte[] sendData = []

    // Empfangene Parameter
    /** Empfangene Sequenznummer */
    long recvSeqNumber = 0
    /** Empfangene Quittungsnummer */
    long recvAckNumber = 0
    /** Empfangenes ACK-Flag*/
    boolean recvAckFlag = false
    /** Empfangenes SYN-Flag*/
    boolean recvSynFlag = false
    /** Empfangenes FIN-Flag*/
    boolean recvFinFlag = false
    /** Empfangene Anwendungsdaten */
    byte[] recvData = []

    /** Retransmission Timeout in ms */
    final int retransTimeout = 1000
    /** Erstmalige Sendeverzögerung eines Paketes in ms */
    final int retransTimeout0 = 0

    //========================================================================================================//
    // TCP ENDE
    //========================================================================================================//


    //========================================================================================================//
    // IPv4 ANFANG
    //========================================================================================================//

    /** IPv4-TTL */
    int ttl = 30
    /** IPv4-Offset */
    int offset = 0
    /** ID des IP-Paketes ist im Beispiel konstant, sollte eigentlich inkrementiert werden */
    int id = 12345
    int protocol = IPPacket.IPPROTO_TCP

    /** Broadcast IP-Adresse <br/>
     *  u.U. auch z.B. 141.20.33.255, in der Vorlesung mehr dazu
     */
    final String broadcastIPAddress = "255.255.255.255"

    //========================================================================================================//
    // IPv4 ENDE
    //========================================================================================================//


    //========================================================================================================//
    // MAC ANFANG
    //========================================================================================================//

    /**
     * Vom Betriebssystem vergebener Gerätename unter dem der Netzwerkadapter ansprechbar ist
     */
    String deviceName
    /** Broadcast MAC-Adresse */
    final String broadcastMacAddress = "ff:ff:ff:ff:ff:ff"

    //========================================================================================================//
    // MAC ENDE
    //========================================================================================================//


    //========================================================================================================//
    // Programm-interne Vereinbarungen ANFANG
    //========================================================================================================//

    /** Daten der Anwendung */
    byte[] applicationData
    Random rand = new Random()
    /** Konfigurations-Objekt */
    ConfigObject config
    //----------------------------------------------------------

    //----------------------------------------------------------
    // jpcap
    /** Repräsentiert das Netzwerk-Interface */
    NetworkInterface device = null
    /** Der Empfänger für MAC-Frames */
    JpcapCaptor receiver = null
    /** Der Sender für MAC-Frames.<br/>
     * Um testen zu können wird "sender" typenlos definiert */
    def sender = null
    //----------------------------------------------------------

    //----------------------------------------------------------
    InetAddress ownInetAddress
    InetAddress httpServerInetAddress
    //----------------------------------------------------------

    //----------------------------------------------------------
    EthernetPacket recvFrame = null
    IPPacket recvIpPacket = null
    TCPPacket recvTCPPacket = null
    TCPPacket sentTCPPacket = null
    //----------------------------------------------------------

    //----------------------------------------------------------
    // Warteschlangen -------------------------------------------
    /** Queue für empfangene TCP-PDUs (Pakete) */
    LinkedBlockingQueue<Packet> recvTCPQueue = new LinkedBlockingQueue()
    /** Queue für zu sendende PDUs */
    DelayQueue<SendContainer> sendQueue = new DelayQueue()
    /** Queue für Kommandos von der Anwendung */
    LinkedBlockingQueue<List> cmdQueue = new LinkedBlockingQueue()
    /** Queue für Ergebnisse zur Anwendung */
    LinkedBlockingQueue<List> resultQueue = new LinkedBlockingQueue()
    //----------------------------------------------------------

    //----------------------------------------------------------
    /** Thread organisiert den Empfang */
    Thread receiverThread

    /** Thread liest die Kommunikations-Queues */
    Thread queueThread

    // Verschiedenes
    /** Wenn true: stoppen die Threads */
    boolean stopThreads = false

    /** Zeitkonstante */
    long sec1 = 1000, sec2 = 2000, sec5 = 5000, sec10 = 10000

    /** Wenn false wird Jpcap nicht initialisiert, das Programm kann dann ohne
     *  Administratorrechte getestet werden */
    boolean withJpcap

    /** Die Finite Zustandsmaschine */
    FiniteStateMachine fsm
    /** Der Anfangszustand der Finiten Zustandsmaschine */
    int newState = State.S_IDLE

    //========================================================================================================//
    // Programm-interne Vereinbarungen ENDE
    //========================================================================================================//


    //========================================================================================================//
    // Methoden ANFANG
    //========================================================================================================//

    /**
     * Konstruktor.<br/>
     * Einstellen der Ablaufumgebung und der Finiten Zustandsmaschine (FSM).
     * @param confFileName Pfad zur Konfigurationsdatei
     * @param environment Name der Ablaufumgebung
     */
    Stack(String confFileName, String environment) {

        // Konfiguration aus Datei lesen
        File confFile = new File(confFileName)
        config = new ConfigSlurper(environment).parse(confFile.toURL())

        // Initialisieren der Ablaufumgebung
        ownIPAddress = config.ownIPAddress
        ownMacAddress = config.ownMacAddress
        httpServerIPAddress = config.httpServerIPAddress
        destinationMacAddress = config.destinationMacAddress
        deviceName = config.deviceName

        // Erzeugung von benötigten binären Darstellungen der IP-Adressen
        ownInetAddress = InetAddress.getByAddress(Utils.ipToByteArray(ownIPAddress))
        httpServerInetAddress = InetAddress.getByAddress(Utils.ipToByteArray(httpServerIPAddress))

        // Initialisieren der FSM
        fsm = new FiniteStateMachine(transitions, State.S_IDLE)
    }

    //========================================================================================================//
    // Protokoll Verarbeitung ANFANG
    //========================================================================================================//

    /**
     * Behandelt jeweils einen neuen Zustand der Zustandsmaschine.
     * @param newState Zu behandelnder Zustand
     */
    void handleStateChange(int newState) {
        switch (newState) {
            case (State.S_SEND_SYN):
                // Verbindungsaufbau beginnen
                sendSynFlag = true
                sendAckNumber = 0
                sendAckFlag = false
                sendFinFlag = false
                sendSeqNumber = rand.nextInt(5000) + 1
                sendData = []
                // senden von SYN
                sendTCPPacket(retransTimeout)
                fsm.fire(Event.E_SEND_SYN)
                break

            case (State.S_SEND_FIN):
                // Verbindungsabbau starten
                sendSynFlag = false
                sendAckFlag = true
                sendFinFlag = true
                sendData = []
                // senden von FIN
                sendTCPPacket(retransTimeout)
                fsm.fire(Event.E_SEND_FIN)
                break

            case (State.S_SEND_ACK_SYN_ACK):
                // SYN+ACK empfangen
                sendSynFlag = false
                sendAckFlag = true
                sendAckNumber = recvSeqNumber + 1
                sendSeqNumber += 1
                sendFinFlag = false
                sendData = []
                // ACK nach SYN_ACK senden
                sendTCPPacket(retransTimeout0)
                fsm.fire(Event.E_ACK_SYN_ACK_SENT)
                break

            case (State.S_RCVD_ACK):
                // ACK empfangen
                if (recvSeqNumber == sendAckNumber) {
                    sendSeqNumber = recvAckNumber
                    fsm.fire(Event.E_WAITING)
                }
                break

            case (State.S_RCVD_DATA):
                // Daten empfangen
                if (recvSeqNumber == sendAckNumber) {
                    sendSynFlag = false
                    sendAckFlag = true
                    sendAckNumber = sendAckNumber + recvData.size()
                    sendFinFlag = false
                    sendData = []
                    // Daten an Anwendung übergeben
                    resultQueue.put([Cmd.DATA, recvData.clone()])
                    recvData = []
                    // ACK für Daten senden
                    sendTCPPacket(retransTimeout0)
                    fsm.fire(Event.E_WAITING)
                }
                break

            case (State.S_SEND_ACK_FIN_ACK):
                // FIN+ACK empfangen
                sendAckFlag = true
                sendFinFlag = false
                sendSeqNumber += 1
                sendAckNumber = recvSeqNumber + 1
                sendData = []
                // ACK nach FIN+ACK senden
                sendTCPPacket(retransTimeout0)
                fsm.fire(Event.E_ACK_FIN_ACK_SENT)
                break

            case (State.S_SEND_DATA):
                // Senden von Anwendungsdaten
                sendSynFlag = false
                sendAckFlag = true
                sendFinFlag = false
                // daten senden
                sendTCPPacket(retransTimeout)
                sendSeqNumber += sendData.size()
                fsm.fire(Event.E_DATA_SENT)
                break

            default:
                // nicht zu behandelnder Zustand oder null bei Fehler
                break
        }
    }

    //------------------------------------------------------------------------------//

    /**
     * Analysiert eine empfangene TCP-PDU.<br/>
     * Bestimmt dazu ein Ereignis, "feuert" die FSM und lässt
     * den neuen Zustand behandeln
     * @param packet Die empfangene TCP-PDU
     */
    void processTCPPacket(TCPPacket packet) {
        recvSeqNumber = packet.sequence
        recvAckNumber = packet.ack_num
        recvAckFlag = packet.ack
        recvFinFlag = packet.fin
        recvSynFlag = packet.syn
        recvData = Utils.concatenateByteArrays(recvData, packet.data)

        // Nur zur Protokollierunng, kann auskommentiert werden
        Utils.writeLog("Stack","processTCPPacket","received: ${recvAckFlag ? "ACK," : ""} ${recvSynFlag ? "SYN," : ""}" +
                "${recvFinFlag ? "FIN," : ""} ${recvSeqNumber}" +
                ",${recvAckNumber}, \"${new String(recvData)}\"")

        int event = 0
        // Ereignis bestimmen
        if (recvAckFlag && recvSynFlag) {event = Event.E_RCVD_SYN_ACK}
        else if (recvFinFlag) {event = Event.E_RCVD_FIN_ACK}
        else if (recvAckFlag && !packet.data.size()) {event = Event.E_RCVD_ACK}
        else if (recvAckFlag && packet.data.size()) {event = Event.E_RCVD_DATA}
        try {
            if (event) {
                // Zustandsübergang bestimmen lassen
                newState = fsm.fire(event)
                if (newState) {
                    // Neuen Zustand behandeln
                    handleStateChange(newState)
                }
            }
        }
        catch (Exception e) {
            Utils.writeLog("Stack","processTCPPacket","${e.getStackTrace()}")
        }
    }

    //------------------------------------------------------------------------------//

    /**
     * Diese Methode wird für jeden empfangenen MAC-Frame von der Library Jpcap aus gerufen.
     * @param recvPacket Das empfangene MAC-Frame
     */
    void receivePacket(Packet recvPacket) {

        // Dient zur Steuerung des Abbruchs des Paketempfangs
        boolean analyze = false

        // Zur Protokollierung
        //Utils.writeLog("Stack","receivePacket", "${recvPacket})

        //------------------------------------------------------//
        // MAC bzw. Verbindungsschicht bzw. Sicherungsschicht
        // Adresse
        recvFrame = recvPacket.datalink as EthernetPacket
        // Vergleich der Ziel-MAC-Adresse
        if (recvFrame.destinationAddress == ownMacAddress ||
                recvFrame.destinationAddress == broadcastMacAddress) {
            // Es ist die eigene MAC-Adresse oder MAC-Broadcast
            analyze = true
        }

        // Protokoll
        if (analyze) {
            switch (recvFrame.frametype) {
            // Test auf IP-PDU
                case (EthernetPacket.ETHERTYPE_IP):
                    recvIpPacket = recvPacket as IPPacket
                    break
            /*
                // Beispiel zur Behandlung anderer PDU-Arten
                case (EthernetPacket.ETHERTYPE_ARP):
                    recvArpPacket = recvArpPacket as ARPPacket
                    recvArpQueue.put(recvArpPacket)
                    // Paketanalyse abbrechen
                    analyze = false
                    break
            */
                default:
                    analyze = false
            }
        }

        //------------------------------------------------------//
        // IP
        // Adresse
        if (analyze) {
            // Vergleich der Ziel-IP-Adresse; Hier fehlt z.B. der Test auf richtige AbsenderAdresse!
            if (recvIpPacket.dst_ip.hostAddress != ownIPAddress &&
                    recvIpPacket.dst_ip.hostAddress != broadcastIPAddress) {
                // Nein - Paket verwerfen
                analyze = false
            }
        }

        // Protokoll
        if (analyze) {
            switch (recvIpPacket.protocol) {
            // Test auf Transportprotokoll-Typ
                case (IPPacket.IPPROTO_TCP):
                    recvTCPPacket = recvPacket as TCPPacket
                    break
                default:
                    analyze = false
            }
        }

        //------------------------------------------------------//
        // TCP
        if (analyze) {
            // Test auf Zielport; Hier fehlt z.B. der Test auf richtigen Absenderport
            if (recvTCPPacket.dst_port == ownPort) {
                // Paket in Empfangs-Queue schreiben
                recvTCPQueue.put(recvTCPPacket)
            }
        }
    }

    //------------------------------------------------------------------------------//

    /**
     * Erzeugt die zur Übergabe an die Library {@link jpcap} notwendige TCPPacket-Instanz.
     * <p/>
     * Die dabei verwendete Struktur ist didaktisch unschön! Die Kapselung erfolgt eigentlich so:<br/>
     * TCP-PDU in IP-PDU in IEEE 803.3-PDU, auf "Deutsch" TCP-Paket in IP-Paket in Ethernet-Frame ;-)<br/>
     * Siehe Unterricht!
     *
     * @param retransTimeout Retransmission Timeout in Millsec
     */
    void sendTCPPacket(long retransTimeout) {

        // TCP
        /*
            public TCPPacket(int src_port,int dst_port,long sequence,long ack_num,
                 boolean urg,boolean ack,boolean psh,boolean rst,
                 boolean syn,boolean fin,boolean rsv1,boolean rsv2,
                 int window,int urgent){
        */

        sentTCPPacket = new TCPPacket(sendSrcPort, sendDstPort, sendSeqNumber, sendAckNumber, false, sendAckFlag, false,
                false, sendSynFlag, sendFinFlag, false, false, sendWindowSize, 0)
        sentTCPPacket.data = sendData

        //------------------------------------------------------//
        // IP
        /*
            public void setIPv4Parameter(int priority, boolean d_flag, boolean t_flag,
                 boolean r_flag, int rsv_tos, boolean rsv_frag, boolean dont_frag,
                 boolean more_frag, int offset, int ident, int ttl, int protocol,
                 InetAddress src, InetAddress dst) {
        */

        sentTCPPacket.setIPv4Parameter(0, false, false, false, 0, false, false, false, offset, id, ttl, protocol,
                ownInetAddress, httpServerInetAddress)

        //------------------------------------------------------//
        // MAC
        EthernetPacket ether = new EthernetPacket()
        ether.frametype = EthernetPacket.ETHERTYPE_IP
        ether.src_mac = Utils.stringToMac(ownMacAddress)
        ether.dst_mac = Utils.stringToMac(destinationMacAddress)
        sentTCPPacket.datalink = ether;

        //------------------------------------------------------//
        // Senden
        sendQueue.put(new SendContainer(sentTCPPacket, 0, retransTimeout))
    }

    //========================================================================================================//
    //  Protokoll Verarbeitung ENDE
    //========================================================================================================//


    //========================================================================================================//
    // Steuerung der internen Abläufe ANFANG
    //========================================================================================================//

    /**
     * Behandlung von Kommandos der Anwendung.
     * @param cmd Liste: [Cmd, String]
     */
    void processCommand(List cmd) {
        int newState
        switch (cmd[0]) {
            case (Cmd.OPEN):
                // Neue Verbindung öffnen
                newState = fsm.fire(Event.E_CONN_REQ)
                if (newState)
                // Neuen Zustand behandeln
                    handleStateChange(newState)
                break
            case (Cmd.CLOSE):
                // Verbindung schließen
                newState = fsm.fire(Event.E_DISCONN_REQ)
                if (newState)
                // Neuen Zustand behandeln
                    handleStateChange(newState)
                break
            case (Cmd.GET):
                // HTTP-PDU senden
                sendData = cmd[1] as byte[]
                newState = fsm.fire(Event.E_SEND_DATA)
                if (newState)
                // Neuen Zustand behandeln
                    handleStateChange(newState)
                break
        }
    }

    //------------------------------------------------------------------------------//

    /**
     *  Liest nacheinander die Queues und behandelt die Inhalte<br/>
     *  Die Sende-Queue wird komplett geleert, dann je ein Element der
     *  anderen Queues.<br/>
     *  Die variable "delay" dient der Verzögerung des Schleifendurchlaufs falls die
     *  Queues leer sind oder werden.<br/>
     *  Die Sende-Queue hat die Besonderheit, das die darin enthaltenen Elemente mit einem Timeout
     *  versehen sind und erst entnommen werden, wenn der Timeout abgelaufen ist.
     *  Dieses Verhalten wird zur Organisierung von TCP-Retransmits verwendet.<p/>
     *  @see SendContainer
     *
     */
    void processQueues() {

        // Steuern die Verzögerung beim Abfragen der Queues
        // Falls Queues leer, verlängert sich das Abfrageintervall
        // Max. Verzögerung des Schleifendurchlaufs
        final int maxDelay = 500
        // Min. Verzögerung des Schleifendurchlaufs
        final int minDelay = 0
        final int deltaDelay = 50
        int delay = maxDelay
        // Wird true wenn während eines Schleifendurchlaufs ein Queue-Element behandelt wurde
        boolean action

        Packet packet
        SendContainer sc

        while (!stopThreads) {
            action = false
            // Alle zu sendenden Pakete behandeln
            while (true) {
                // "poll()" liefert ein Element (mit abgelaufenem Timeout) aus der Queue oder null
                // falls kein Element mit abgelaufenem Timeout vorhanden ist; blockiert nie
                sc = sendQueue.poll()
                // Wenn kein Paket in Queue: Schleife verlassen
                if (!sc) break

                packet = sc.packet as Packet

                // Wenn das Paket ein TCP-Paket ist und es bereits quittiert wurde: nicht nochmals senden
                if (packet instanceof TCPPacket) {
                    if (packet.sequence >= recvAckNumber) {

                        // Nur zur Protokollierung, kann auskommentiert werden
                        Utils.writeLog("Stack", "processQueues", "sending: ${packet.ack ? "ACK, " : ""}"+
                                "${packet.syn ? "SYN, " : ""}${packet.fin ? "FIN, " : ""}${packet.sequence}, " +
                                "${packet.ack_num}, \"${new String(packet.data)}\"")

                        // Paket ist noch nicht quittiert also senden
                        sender.sendPacket(packet)
                        // Soll nochmaliges Senden nach Retransmission Timeout organisiert werden?
                        if (sc.timeout > 0) {
                            // Paket für nochmaliges Senden vorbereiten
                            sendQueue.put(new SendContainer(packet, sc.timeout, sc.timeout))
                        }
                    }
                }
                // Kein TCP-Paket
                else {
                    // Paket wird gesendet
                    sender.sendPacket(sc.packet as Packet)
                    if (sc.timeout > 0) {
                        // Paket für nochmaliges Senden vorbereiten
                        sendQueue.put(new SendContainer(sc.packet, sc.timeout, sc.timeout))
                    }
                }
                action = true
            }

            // Ein einzelnes Empfangspaket behandeln
            if (!recvTCPQueue.isEmpty()) {
                processTCPPacket(recvTCPQueue.take() as TCPPacket)
                action = true
            }

            // Ein einzelnes Kommando behandeln
            if (!cmdQueue.isEmpty()) {
                processCommand(cmdQueue.take())
                action = true
            }

            // Verzögerung organisieren
            if (action) delay -= deltaDelay else delay += deltaDelay
            if (delay < 0) delay = minDelay
            else if (delay > maxDelay) delay = maxDelay
            // Nächsten Schleifendurchlauf verzögern
            sleep(delay)
        }
    }

    //========================================================================================================//
    // Steuerung der internen Abläufe ENDE
    //========================================================================================================//


    //========================================================================================================//
    // Steuerung des Stacks ANFANG
    //========================================================================================================//

    /**
     * Starten des Netzwerkstacks
     * @param wJp Wenn false wird Jpcap nicht initialisiert, Stack kann dann ohne
     * Administratorrechte jedoch auch ohne Netzwerkkommunikation gestestet werden
     */
    void start(boolean wJp = true) {

        // Kontrolliert die Initialsierung von Jpcap
        withJpcap = wJp

        if (withJpcap) {
            // jpcap initialisieren
            // Netzwerk-Device initialisieren
            device = Utils.getDevice(deviceName)

            receiver = JpcapCaptor.openDevice(device, 65535, false, 20)

            // receiver liefert sender mit entsprechenden Parametern
            sender = receiver.getJpcapSenderInstance()

            // Wenn dieser Filter entfernt wird, werden alle MAC-Frames, die zu der Netzwerkkarte
            // des Rechners gesendet werden, empfangen
            // TODO: "port 80" angeben?, sonst wird z.B. auch ssh ... verarbeitet
            receiver.setFilter("tcp and host ${httpServerIPAddress} and host ${ownIPAddress}", true)

            // Receiver als thread starten
            receiverThread = Thread.start {receiver.loopPacket(-1, this)}
        }

        // Thread zur Organisation der Queues starten
        queueThread = Thread.start { processQueues() }
    }

    //------------------------------------------------------------------------------//

    /**
     * Stoppen des Netzwerkstacks
     */
    void stop() {
        // Wenn Library Jpcap verwendet wurde
        if (withJpcap) {
            // Stoppen des Empfangs-Threads
            receiver.breakLoop()
            // Verzögerung für u.U. letztes Paket (FIN+ACK+FIN):
            sleep(sec1)
            // Warten auf Stoppen des Threads
            receiverThread.join()
        }

        // Stoppen des processQueues-Threads
        stopThreads = true

        // Warten auf Stoppen des Threads
        queueThread.join()
    }

    //------------------------------------------------------------------------------//

    /**
     * Herstellen einer TCP-Verbindung zu einem HTTP-Dienst ("Web-Server").
     */
    void open() {
        // Übergabe des Kommandos
        cmdQueue.put([Cmd.OPEN])
        // Warten auf abgeschlossenen Verbindungsaufbau (hier sollte ein Timeout verwendet werden)
        while (fsm.getState() != State.S_WAITING) {}
    }

    //------------------------------------------------------------------------------//

    /**
     * Schließen der TCP-Verbindung.
     */
    void close() {
        // Übergabe des Kommandos
        cmdQueue.put([Cmd.CLOSE])
        // Warten auf Beendigung der Verbindung (hier sollte ein Timeout verwendet werden)
        while (fsm.getState() != State.S_IDLE) {}
    }

    //------------------------------------------------------------------------------//

    /**
     * Generierung einer HTTP-GET-PDU.
     * @param page Der Pfad zum HTML-Dokument
     * @return HTML-Dokument (hier: nur das erste Teilstück)
     */
    String sendRequest(String page) {

        // HTTP-PDU
        byte[] applicationData = "GET ${page} HTTP/1.1\n\n".getBytes()

        String result

        // Übergabe des Kommandos und der HTTP-PDU
        cmdQueue.put([Cmd.GET, applicationData])

        // Warten auf die Antwort des HTTP-Servers
        // Achtung: Es wird nur das erste vom Server empfangene Paket an die Anwendung geliefert!!
        result = new String(resultQueue.take()[1] as byte[])

        // Warten, bis Server (wahrscheinlich) alles gesendet hat
        // Anstatt zu warten besser untersuchen, ob mehr Daten zu empfangen sind:
        // "HTTP: Content-Length" auswerten
        // ...
        sleep(sec10)

        return result
    }

    //========================================================================================================//
    // Steuerung des Stacks ENDE
    //========================================================================================================//


    //========================================================================================================//
    // Methoden ENDE
    //========================================================================================================//
}

//========================================================================================================//
// Stack-Klasse ENDE
//========================================================================================================//
