import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Server {

    // hier stellen wir den port ein
    private static final int PORT = 6767;
    private static final int GLOBAL_CHAT_ID = 99;
    // lock damit zwei threads nicht gleichzeitig daten kaputt machen
    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // einfache daten im ram
    private static final Map<String, String> users = new HashMap<>();
    private static final Map<Integer, Chat> chats = new HashMap<>();
    private static int nextChatId = 1;

    // global chat ist immer da
    static {
        chats.put(GLOBAL_CHAT_ID, new Chat(GLOBAL_CHAT_ID));
    }

    public static void main(String[] args) {
        System.out.println("server startet auf port " + PORT);

        // server wartet immer auf neue clients
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("server laeuft");

            while (true) {
                Socket socket = serverSocket.accept();
                // jeder client bekommt seinen eigenen thread
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("fehler beim starten vom server: " + e.getMessage());
        }
    }

    // hier kommt genau ein befehl vom client rein
    private static void handleClient(Socket socket) {
        try (
            Socket client = socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8)
        ) {
            String line = in.readLine();
            if (line == null || line.isBlank()) {
                writeError(out, "leerer befehl");
                return;
            }

            String[] parts = line.split(" ", 2);
            String command = parts[0].toUpperCase();
            String payload = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "REGISTER":
                    handleRegister(out, payload);
                    break;
                case "LOGIN":
                    handleLogin(out, payload);
                    break;
                case "CREATE_CHAT":
                    handleCreateChat(out, payload);
                    break;
                case "LIST_CHATS":
                    handleListChats(out, payload);
                    break;
                case "SEND_MESSAGE":
                    handleSendMessage(out, payload);
                    break;
                case "GET_MESSAGES":
                    handleGetMessages(out, payload);
                    break;
                default:
                    writeError(out, "unbekannter befehl: " + command);
            }
        } catch (IOException e) {
            System.out.println("fehler bei client verbindung: " + e.getMessage());
        }
    }

    // user registrieren
    private static void handleRegister(PrintWriter out, String payload) {
        String[] args = payload.split("\\|", 2);
        if (args.length != 2) {
            writeError(out, "register braucht: username|passwort");
            return;
        }

        String username = args[0].trim();
        String password = args[1].trim();

        if (username.isEmpty() || password.isEmpty()) {
            writeError(out, "username und passwort duerfen nicht leer sein");
            return;
        }

        synchronized (LOCK) {
            if (users.containsKey(username)) {
                writeError(out, "user existiert bereits");
                return;
            }
            users.put(username, password);
        }

        writeOk(out, "registrierung erfolgreich");
    }

    // user anmelden
    private static void handleLogin(PrintWriter out, String payload) {
        String[] args = payload.split("\\|", 2);
        if (args.length != 2) {
            writeError(out, "login braucht: username|passwort");
            return;
        }

        String username = args[0].trim();
        String password = args[1].trim();

        synchronized (LOCK) {
            String savedPassword = users.get(username);
            if (savedPassword == null || !savedPassword.equals(password)) {
                writeError(out, "login fehlgeschlagen");
                return;
            }
        }

        writeOk(out, "login erfolgreich");
    }

    // neuen chat zwischen zwei usern erstellen
    private static void handleCreateChat(PrintWriter out, String payload) {
        String[] args = payload.split("\\|", 2);
        if (args.length != 2) {
            writeError(out, "create_chat braucht: user1|user2");
            return;
        }

        String user1 = args[0].trim();
        String user2 = args[1].trim();

        if (user1.isEmpty() || user2.isEmpty()) {
            writeError(out, "usernamen duerfen nicht leer sein");
            return;
        }

        if (user1.equals(user2)) {
            writeError(out, "ein chat braucht zwei verschiedene user");
            return;
        }

        synchronized (LOCK) {
            if (!users.containsKey(user1) || !users.containsKey(user2)) {
                writeError(out, "einer der user existiert nicht");
                return;
            }

            Chat existing = findChatBetween(user1, user2);
            if (existing != null) {
                out.println("OK|chat existiert bereits");
                out.println("CHAT_ID|" + existing.id);
                out.println("END");
                return;
            }

            int chatId = nextChatId;
            nextChatId++;
            if (chatId == GLOBAL_CHAT_ID) {
                chatId = nextChatId;
                nextChatId++;
            }

            Chat chat = new Chat(chatId, user1, user2);
            chats.put(chatId, chat);

            out.println("OK|chat erstellt");
            out.println("CHAT_ID|" + chatId);
            out.println("END");
        }
    }

    // alle chats von einem user auflisten
    private static void handleListChats(PrintWriter out, String payload) {
        String username = payload.trim();
        if (username.isEmpty()) {
            writeError(out, "list_chats braucht: username");
            return;
        }

        List<Chat> userChats = new ArrayList<>();

        synchronized (LOCK) {
            if (!users.containsKey(username)) {
                writeError(out, "user existiert nicht");
                return;
            }

            for (Chat chat : chats.values()) {
                if (chat.id == GLOBAL_CHAT_ID || chat.participants.contains(username)) {
                    userChats.add(chat);
                }
            }
        }

        out.println("OK|chats gefunden: " + userChats.size());
        for (Chat chat : userChats) {
            if (chat.id == GLOBAL_CHAT_ID) {
                out.println("CHAT|" + chat.id + "|global");
            } else {
                out.println("CHAT|" + chat.id + "|" + String.join(",", chat.participants));
            }
        }
        out.println("END");
    }

    // nachricht in einen chat schreiben
    private static void handleSendMessage(PrintWriter out, String payload) {
        String[] args = payload.split("\\|", 3);
        if (args.length != 3) {
            writeError(out, "send_message braucht: sender|chatid|text");
            return;
        }

        String sender = args[0].trim();
        String chatIdText = args[1].trim();
        String text = args[2].trim();

        if (sender.isEmpty() || chatIdText.isEmpty() || text.isEmpty()) {
            writeError(out, "sender chatid und text sind pflicht");
            return;
        }

        int chatId;
        try {
            chatId = Integer.parseInt(chatIdText);
        } catch (NumberFormatException e) {
            writeError(out, "ungueltige chatid");
            return;
        }

        synchronized (LOCK) {
            Chat chat = chats.get(chatId);
            if (chat == null) {
                writeError(out, "chat nicht gefunden");
                return;
            }

            if (chatId == GLOBAL_CHAT_ID) {
                if (!users.containsKey(sender)) {
                    writeError(out, "user existiert nicht");
                    return;
                }
            } else if (!chat.participants.contains(sender)) {
                writeError(out, "du bist nicht in diesem chat");
                return;
            }

            chat.messages.add(new Message(sender, text, LocalDateTime.now().format(TIME_FORMAT)));
        }

        writeOk(out, "nachricht gespeichert");
    }

    // nachrichten aus einem chat holen
    private static void handleGetMessages(PrintWriter out, String payload) {
        String[] args = payload.split("\\|", 2);
        if (args.length != 2) {
            writeError(out, "get_messages braucht: username|chatid");
            return;
        }

        String username = args[0].trim();
        String chatIdText = args[1].trim();

        int chatId;
        try {
            chatId = Integer.parseInt(chatIdText);
        } catch (NumberFormatException e) {
            writeError(out, "ungueltige chatid");
            return;
        }

        List<Message> messages = new ArrayList<>();

        synchronized (LOCK) {
            Chat chat = chats.get(chatId);
            if (chat == null) {
                writeError(out, "chat nicht gefunden");
                return;
            }

            if (chatId == GLOBAL_CHAT_ID) {
                if (!users.containsKey(username)) {
                    writeError(out, "user existiert nicht");
                    return;
                }
            } else if (!chat.participants.contains(username)) {
                writeError(out, "du bist nicht in diesem chat");
                return;
            }

            messages.addAll(chat.messages);
        }

        out.println("OK|nachrichten");
        for (Message message : messages) {
            out.println("MSG|" + message.sender + "|" + message.text + "|" + message.time);
        }
        out.println("END");
    }

    // schauen ob chat zwischen user1 und user2 schon da ist
    private static Chat findChatBetween(String user1, String user2) {
        Set<String> pair = new HashSet<>();
        pair.add(user1);
        pair.add(user2);

        for (Chat chat : chats.values()) {
            if (chat.participants.equals(pair)) {
                return chat;
            }
        }
        return null;
    }

    // standard antwort bei erfolg
    private static void writeOk(PrintWriter out, String message) {
        out.println("OK|" + message);
        out.println("END");
    }

    // standard antwort bei fehler
    private static void writeError(PrintWriter out, String message) {
        out.println("ERROR|" + message);
        out.println("END");
    }

    // ein chat hat id teilnehmer und nachrichten
    private static class Chat {
        private final int id;
        private final Set<String> participants = new HashSet<>();
        private final List<Message> messages = new ArrayList<>();

        private Chat(int id) {
            this.id = id;
        }

        private Chat(int id, String user1, String user2) {
            this.id = id;
            participants.add(user1);
            participants.add(user2);
        }
    }

    // eine nachricht hat sender text und uhrzeit
    private static class Message {
        private final String sender;
        private final String text;
        private final String time;

        private Message(String sender, String text, String time) {
            this.sender = sender;
            this.text = text;
            this.time = time;
        }
    }
}
