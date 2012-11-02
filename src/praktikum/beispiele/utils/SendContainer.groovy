package praktikum.beispiele.utils

import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import jpcap.packet.Packet

/**
 * Container für ein zu sendendes Paket in der Sende-Queue.
 * Der erste Zeitwert gibt die erste Verweilzeit des Pakets in der Warteschlange an, bevor es entnommen
 * werden kann, der zweite Zeitwert alle nachfolgenden Verweilzeiten.
 * @see java.util.concurrent.DelayQueue
 */
class SendContainer implements Delayed {
    /** Das zu verwaltende Paket*/
    Packet packet
    /** Der Zeitpunkt der Aufnahme in die Warteschlange */
    long insertionTime
    /** Die aktuelle Verweilzeit */
    long currTimeout
    /** Die Verweilzeit */
    long timeout

    /**
     * Der Konstruktor
     * @param item Das zu verwaltende Objekt
     * @param firstTimeout Der Wert der ersten Verzögerung in Millisec, üblicherweise 0
     * @param timeout Der nächste (und alle weiteren) Verzögerungs-Werte in Millisec
     */
    SendContainer(Packet item, long firstTimeout, long timeout) {
        packet = item
        insertionTime = System.currentTimeMillis()
        // der erste timeout
        this.currTimeout = firstTimeout
        // alle timeouts danach
        this.timeout = timeout

    }

    /**
     * Muß implementiert werden, interne Verwendung.
     * @param unit Die verlangte Maßeinheit der Verzögerungseit
     * @return Die noch verbleibende Verzögerungszeit
     */
    long getDelay(TimeUnit unit) {
        long tmp = unit.convert((insertionTime - System.currentTimeMillis()) + currTimeout,
                TimeUnit.MILLISECONDS)
        return tmp
    }

    /**
     * Vergleicht die noch verbleibende Verzögerungen zweier Objekte.<br/>
     * Muß implementiert werden, interne Verwendung
     * @param o das zu vergleichende Objekt
     * @return -1 für kürzer, 0 für gleich, 1 für länger
     */
    int compareTo(Delayed o) {
        int ret = 0
        SendContainer sc = o as SendContainer

        if ( this.currTimeout < sc.currTimeout ) ret = -1
        else if ( this.currTimeout > sc.currTimeout ) ret = 1
        else if ( this.insertionTime == sc.insertionTime ) ret = 0

        return ret
    }
}
