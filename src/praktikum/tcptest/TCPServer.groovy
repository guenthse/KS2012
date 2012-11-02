package praktikum.tcptest

// Portnummer
int port = 4444

// Server-Socket erzeugen
def server = new ServerSocket(4444)

while(true) {

    // Auf Verbindungsanforderung warten
    server.accept { socket ->
        socket.withStreams { input, output ->
            def reader = input.newReader()

            // Vom Netzwerk empfangen
            def buffer = reader.readLine()

            // Antwort senden
            output << "Error 404: File not found: " + buffer.split(" ")[1] + "\n"
        }
    }
}