package praktikum.beispiele.utils

import jpcap.NetworkInterface
import jpcap.JpcapCaptor
import jpcap.JpcapSender

/**
 * Eine Sammlung von nützlichen Methoden.
 */
class Utils {

    // ######################################################################## #

    /**
     * Zeigt eine Liste der verfügbaren Netzwerk-Devices (-Interfaces) an.<br/>
     * libjpcap.so muss installiert sein (setzt für Installation und Verwendung Administartorrechte voraus!)
     */
    public static void listDevices() {
        //Obtain the list of network interfaces
        List<NetworkInterface> devices = JpcapCaptor.getDeviceList()

        //for each network interface
        devices.each { device ->
            //print out its name and description
            println("${device.name} (${device.description})")

            //print out its datalink name and description
            println(" datalink: ${device.datalink_name} (${device.datalink_description ?: "Keine Beschreibung"})")

            //print out its MAC address
            print(" MAC address:")

            device.mac_address.each { address ->
                print("${Integer.toHexString((address & 0xff) as int)} :")
            }

            println()

            //print out its IP address, subnet mask and broadcast address
            device.addresses.each {
                println(" address: ${it.address} ${it.subnet} ${it.broadcast}")
            }
            println()
        }
    }

    //-------------------------------------------------------------------------------//

    /**
     * Liefert ein Netzwerk-Device-Objekt.
     * libjpcap.so muss installiert sein (setzt für Installation und Verwendung Administartorrechte voraus!)
     * @param name Name des Interfaces, z.B. "eth0", "en0" "lo0", "lan1".
     * Die Namen erfährt man z.B. unter Unix durch das Kommando "ifconfig"
     * @return Das Netzwerk-Interface-Objekt
     */
    public static NetworkInterface getDevice(String name) {
        //Obtain the list of network interfaces
        List<NetworkInterface> devices = JpcapCaptor.getDeviceList()
        NetworkInterface device = devices.find {
            it.name == name
        }
        return device
    }

    //-------------------------------------------------------------------------------//

    /**
     * Öffnet das Netzwerk-Device zur Verwendung.
     * libjpcap.so muss installiert sein (setzt für Installation und Verwendung Administartorrechte voraus!)
     * @param device Name des Interfaces
     * @return Liefert ein Jpcap-Sender-Objekt
     */
    public static JpcapSender openDevice(NetworkInterface device) {
        //open a network interface to send a packet to
        return JpcapSender.openDevice(device)
    }

    // ######################################################################## #

    /**
     * Konvertiert eine Zeichenkettendarstellung einer MAC-Adresse in ein Bytefeld.
     * @param sMac String in der Form "01:02:03:04:05:06"
     * @return Bytefeld in der Form [1,2,3,4,5,6]
     */
    public static byte[] stringToMac(String sMac) {
        return sMac.replace(':', '').decodeHex()
    }

    //-------------------------------------------------------------------------------//

    /**
     * Konvertiert eine Zeichenkettendarstellung einer IPv4-Adresse in ein Bytefeld.
     * @param sIp String in der Form "10.1.34.240"
     * @return Bytefeld in der Form [10,1,34,240]
     */
    public static byte[] ipToByteArray(String sIp) {
        return sIp.tokenize('.').collect() {it.toInteger()}
    }

    // ######################################################################## #

    /**
     Zerteilt die Daten in max. size grosse Teile (Pakete),
     liefert eine Liste mit den Paketen

     @param daten die zu zerteilenden Daten
     @param size max. Länge der Pakete
     @return die Liste mit maximal size-langen Datenfragmenten
     */
    public static List fragment(byte[] daten, int size) {
        // Leere Liste erzeugen
        List pakete = []
        int delta
        int i = 0
        int l = daten.size()

        while (l > 0) {
            if ((l - size) > 0) {
                // Paket wird size Byte lang
                delta = size - 1
                l -= size
            }
            else {
                delta = l - 1
                l = 0
            }
            byte[] p = new byte[delta + 1]
            System.arraycopy(daten[i..i + delta] as byte[], 0, p, 0, delta + 1)
            pakete.add(p)
            i += size
        }
        return pakete
    }
    //-------------------------------------------------------------------------------//

    /**
     * Fügt zwei Bytefelder zusammen.
     * @param a Bytefeld 1
     * @param b Bytefeld 2
     * @return Bytefeld mit Inhalten von a und b
     */
    public static byte[] concatenateByteArrays(byte[] a, byte[] b) {
        return reassemble([a,b])
    }

    // ------------------------------------------------------ #

    /**
     Fügt die Elemente (Byte-Arrays) einer Liste zusammen.

     @param pakete Liste mit Datenfragmenten
     @return Daten als ByteArray
     */
    public static byte[] reassemble(List pakete) {
        byte[] daten
        int laenge = 0
        int offset = 0
        int len

        // Gesamtlänge der Daten bestimmen und ByteArray anlegen
        for (teil in pakete) {
            laenge += (teil as byte[]).length
        }

        // Beispiel für "groovysche" Notationen der Schleife:
        // pakete.each {
        //   laenge += (it as byte[]).length
        // }
        // oder:
        // laenge = pakete.inject(0) {len, p -> len += (p as byte[]).length} as int }

        daten = new byte[laenge]

        for (teil in pakete) {
            len = (teil as byte[]).length
            System.arraycopy(teil, 0, daten, offset, len)
            offset += len
        }
        return daten
    }

    // ######################################################################## #

    /**
     Legt fest, ob die Methode writeLog als Debug-Meldungen gekennzeichnete Aufrufe ausführt.<br/>
     true: debug-Meldungen werden ausgegeben,<br/>
     false: debug-Meldungen werden nicht ausgegeben
     */
    private static DEBUG = true

    // ==========================================================================

    /**
     Schreibt eine Protokollmeldung formatiert an die Standard-Ausgabe.
     <p/>
     enthält die Variable DEBUG true, werden auch debugging-Meldungen angezeigt<br>
     Verwendung:<br>
     writeLog("Klasse A", "Methode B", "Hier", false) // normale Meldung<br>
     writeLog("Kl A", "Meth C", "var X: ${varX}", true) // Debug-Meldung<br>

     @param klasse Name der Klasse, in der die Meldung erzeugt wird
     @param methode Name der Methode, welche die Meldung erzeugt
     @param kommentar die Meldung
     @param debug Aufruf enthält debug-Meldung wenn true
     */
    public static void writeLog(String klasse, String methode, String kommentar, Boolean debug = true) {
        long ms = System.currentTimeMillis() % 1000

        if (!debug || DEBUG) {
            printf("%19s.%03d: %s - %s - %s\n", [new Date().format("yyyy-MM-dd-HH-mm-ss"),
                    ms, klasse, methode, kommentar])
        }
    }

    // ######################################################################## #

    /**
     * Liest eine Zeile von der Standard-Eingabe
     *
     * @return Eingabezeile
     */
    public static String readLine() {
        return System.in.newReader().&readLine()
    }
}
