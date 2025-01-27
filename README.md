# ds-project

***

Dies ist ein Simples Chatprogramm.

##Setup
- Alles Kompilieren z.B. mit:
    ```bash
    javac -d bin -sourcepath src src/de/luh/vss/chat/**/*.java
    ```
- Server starten z.B. mit:
    ```bash
    java -cp bin de.luh.vss.chat.server.ChatServer
    ```

- Ein weiteres Terminal starten und mindestens Client starten:
    ```bash
    java -cp bin de.luh.vss.chat.client.ChatClient
    ```

##Features
- Es können Nachrichten zwischen den Usern ausgetauscht werden
- Alle eigenen und an einen adressierten Nachrichten werden in einem im Terminal verlinkten URL im Browser angezeigt
- Man bekommt einen Hinweis, wenn eine UserId bereits vergeben ist
- Man wird darauf hingewiesen, wenn man an eine nicht vergebene UserId schreiben möchte, dass diese nicht existiert
- Man wird darauf hingewiesen, wenn man an eine nicht mehr verbundene UserId schreiben möchte, dass diese offline ist
- Clients schicken automatisch Hearbeat Signale an den Server

