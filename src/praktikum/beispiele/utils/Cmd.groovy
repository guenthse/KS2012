package praktikum.beispiele.utils

/**
 * Kommandos der Anwendung and den Netzwerkstack und Antworten an sie.<br/>
 * Ein Aufzählungstyp, Bestandteil des Protokolls zwischen Anwendung und Netzwerkstack<br/>
 * Siehe: {@link praktikum.beispiele.beispiel1.Stack#open},{@link praktikum.beispiele.beispiel1.Stack#close},
 * {@link praktikum.beispiele.beispiel1.Stack#sendRequest},{@link praktikum.beispiele.beispiel1.Stack#handleStateChange}
 */
enum Cmd {
    /** Anwendung -> Netzwerk-Stack: Verbindung herstellen */
    OPEN,
    /** Anwendung -> Netzwerk-Stack: Verbindung trennen */
    CLOSE,
    /** Anwendung -> Netzwerk-Stack: HTTP-Dokument vom Server holen */
    GET,
    /** Netzwerk-Stack -> Anwendung: Das Resultat an Anwendung */
    DATA
}
