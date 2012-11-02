#!/usr/bin/env groovy

package praktikum.beispiele.beispiel1


//========================================================================================================//
// Importe ANFANG
//========================================================================================================//

import praktikum.beispiele.utils.Utils

//========================================================================================================//
// Importe ENDE
//========================================================================================================//


//========================================================================================================//
// Hauptprogramm ANFANG
//========================================================================================================//


//========================================================================================================//
// HttpBeispiel-Klasse ANFANG
//========================================================================================================//

class HttpBeispiel {

    //========================================================================================================//
    // Konfiguration der Ablaufumgebung ANFANG
    //========================================================================================================//

    /** Name der Konfigurationsdatei für die Ablaufumgebung */
    static final String confFile = "env.conf"

    /** Hier muß eine der Ablaufumgebungen eingestellt werden, die in der Konfigurationsdatei "env.conf"
     * definiert sind (z.B. "home" oder "uni").
     * Der Eintrag wird durch den optionalen Kommandozeilenparameter "e" überschrieben.
     * Beim Start mit start.sh <br/>
     * . start.sh [-e environment]<br/>
     * oder als jar-file:<br/>
     * unzip KS_Praktikum.jar; . startbin.sh [-e environment]
     */
    static String environment = "unikabel"

    /** Pfad zur Konfigurationsdatei für die Ablaufumgebung <br/>
     * Der Eintrag wird durch den Kommandozeilenparameter "p" überschrieben.<br/>
     * */
    static String confFileName = "src/praktikum/beispiele/"

    //========================================================================================================//
    // Konfiguration der Ablaufumgebung ENDE
    //========================================================================================================//

    /**
     * Das Hauptprogramm - die "Anwendung".<br/>
     * Es wird ein HTTP-Dokument ("Web-Seite") von einem Server angefordert.<br/>
     * Alle notwendigen umgebungsabhängigen Angaben, wie IP-Adressen usw. müssen in die Datei
     * "src/praktikum/beispiele/env.conf" eingetragen werden.<br/>
     * Die Methode {@link praktikum.beispiele.beispiel1.Stack#sendRequest sendRequest} kehrt mit einem HTML-Dokument
     * (oder Teilen davon) zurück.
     */
    static void main(String[] args)  {
        // Behandlung von Kommandozeilenoptionen
        def cli = new CliBuilder(usage: 'java | groovy ... HttpBeispiel:')
        cli.h(required: false, 'Hilfe anzeigen')
        cli.e(required: false, args: 1, 'Name der zu verwendenden Ablaufumgebung')
        cli.p(required: false, args: 1, "Pfad zu \'env.conf\', also z.B. \'praktikum/beispiele/\', nur beim Start von der Kommandozeile")

        // Kommandozeilenparameter parsen
        def options = cli.parse(args)

        // Hilfe anzeigen
        if (options.h) {
            cli.usage()
            return
        }

        // Ablaufumgebung setzen
        if (options.e) {
            environment = options.e
        }

        // Pfad zur Konfigurationsdatei der Ablaufumgebungen setzen
        confFileName += confFile
        if (options.p) {
            confFileName = options.p + confFile
        }

        // ------------------------------------------------------------------------- //

        // Den Netzwerkstack initialisieren
        Stack stack = new Stack(confFileName, environment)

        // Arbeit des Netzwerkstacks beginnen
        stack.start()

        // Verbindung zum HTTP-Dienst ("Web-Server") öffnen
        stack.open()

        // HTML-Dokument anfordern
        String reply = stack.sendRequest("/xyz")

        // HTML-Dokument anzeigen
        Utils.writeLog("Stack", "start", "\n********* Antwort *********\nAntwort: ${reply}\n*****************************\n")

        // Verbindung zum HTTP-Dienst ("Web-Server") schließen
        stack.close()

        // Arbeit des Netzwerkstacks stoppen
        stack.stop()
    }
}

//========================================================================================================//
// HttpBeispiel-Klasse ENDE
//========================================================================================================//


//========================================================================================================//
// Hauptprogramm ENDE
//========================================================================================================//
