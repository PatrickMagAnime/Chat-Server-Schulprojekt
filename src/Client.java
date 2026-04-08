import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class Client {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 6767;
    private static final int GLOBAL_CHAT_ID = 99;

    private static final Color BG = new Color(18, 18, 18);
    private static final Color PANEL_BG = new Color(28, 28, 28);
    private static final Color FG = new Color(220, 220, 220);
    private static final Color INPUT_BG = new Color(40, 40, 40);
    private static final Color ERROR = new Color(220, 110, 110);
    private static final Color OK = new Color(130, 220, 140);

    private final JFrame frame = new JFrame("einfacher chat client");

    private final JTextField hostField = new JTextField(DEFAULT_HOST);
    private final JTextField portField = new JTextField(String.valueOf(DEFAULT_PORT));
    private final JLabel connectStatus = new JLabel(" ");

    private final JTextField userField = new JTextField();
    private final JTextField passField = new JTextField();
    private final JLabel authStatus = new JLabel(" ");

    private final JTextField otherUserField = new JTextField();
    private final JTextField chatIdField = new JTextField();
    private final JTextField messageField = new JTextField();

    private final JTextArea chatArea = new JTextArea();
    private final JTextArea outputArea = new JTextArea();

    private JPanel connectPanel;
    private JPanel authPanel;
    private JPanel mainPanel;

    private String loggedInUser = null;
    private String lastChatRender = "";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().start());
    }

    private void start() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);

        connectPanel = buildConnectPanel();
        authPanel = buildAuthPanel();

        showConnect();
        frame.setVisible(true);

        Timer timer = new Timer(1200, e -> refreshChatView(false));
        timer.start();
    }

    private JPanel buildConnectPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panel = new JPanel(new GridLayout(4, 1, 6, 6));
        stylePanel(panel, "server verbindung");

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.setOpaque(false);
        styleInput(hostField, 18);
        row1.add(label("ip"));
        row1.add(hostField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.setOpaque(false);
        styleInput(portField, 8);
        row2.add(label("port"));
        row2.add(portField);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.setOpaque(false);
        JButton connectButton = makeButton("verbinden", e -> tryConnect());
        row3.add(connectButton);

        connectStatus.setForeground(FG);

        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        panel.add(connectStatus);

        root.add(panel, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildAuthPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel panel = new JPanel(new GridLayout(5, 1, 6, 6));
        stylePanel(panel, "anmeldung");

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.setOpaque(false);
        styleInput(userField, 18);
        row1.add(label("user"));
        row1.add(userField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.setOpaque(false);
        styleInput(passField, 18);
        row2.add(label("pass"));
        row2.add(passField);

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.setOpaque(false);
        JButton registerButton = makeButton("registrieren", e -> register());
        JButton loginButton = makeButton("anmelden", e -> login());
        JButton backButton = makeButton("zurueck", e -> showConnect());
        row3.add(registerButton);
        row3.add(loginButton);
        row3.add(backButton);

        authStatus.setForeground(FG);

        panel.add(row1);
        panel.add(row2);
        panel.add(row3);
        panel.add(new JLabel(" "));
        panel.add(authStatus);

        root.add(panel, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildMainPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new GridLayout(2, 1, 6, 6));
        top.setOpaque(false);

        JPanel chatControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stylePanel(chatControls, "chat");
        styleInput(otherUserField, 10);
        JButton createChatButton = makeButton("chat starten", e -> createChat());
        JButton listChatsButton = makeButton("meine chats", e -> listChats());
        JButton globalButton = makeButton("global chat", e -> switchToGlobalChat());
        JButton logoutButton = makeButton("logout", e -> logout());
        chatControls.add(label("mit user"));
        chatControls.add(otherUserField);
        chatControls.add(createChatButton);
        chatControls.add(listChatsButton);
        chatControls.add(globalButton);
        chatControls.add(logoutButton);

        JPanel msgControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stylePanel(msgControls, "nachrichten");
        styleInput(chatIdField, 6);
        styleInput(messageField, 28);
        JButton sendButton = makeButton("senden", e -> sendMessage());
        msgControls.add(label("chat-id"));
        msgControls.add(chatIdField);
        msgControls.add(label("text"));
        msgControls.add(messageField);
        msgControls.add(sendButton);

        top.add(chatControls);
        top.add(msgControls);

        chatArea.setEditable(false);
        styleArea(chatArea);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(FG), "chatfenster"));

        outputArea.setEditable(false);
        styleArea(outputArea);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(FG), "ausgabe"));

        JPanel center = new JPanel(new GridLayout(2, 1, 8, 8));
        center.setOpaque(false);
        center.add(chatScroll);
        center.add(outputScroll);

        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private void tryConnect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            setConnectStatus("fehler ungueltiger port", false);
            return;
        }

        try (Socket ignored = new Socket(host, port)) {
            setConnectStatus("verbindung erfolgreich", true);
            showAuth();
        } catch (IOException e) {
            setConnectStatus("fehler server nicht gefunden", false);
        }
    }

    private void register() {
        String user = userField.getText().trim();
        String pass = passField.getText().trim();
        List<String> response = sendCommand("REGISTER " + user + "|" + pass);
        printResponse(response);

        if (!response.isEmpty() && response.get(0).startsWith("OK|")) {
            setAuthStatus("registrierung ok", true);
        } else {
            setAuthStatus("registrierung fehlgeschlagen", false);
        }
    }

    private void login() {
        String user = userField.getText().trim();
        String pass = passField.getText().trim();
        List<String> response = sendCommand("LOGIN " + user + "|" + pass);
        printResponse(response);

        if (!response.isEmpty() && response.get(0).startsWith("OK|")) {
            loggedInUser = user;
            setAuthStatus("login ok", true);
            showMain();
        } else {
            setAuthStatus("login fehlgeschlagen", false);
        }
    }

    private void createChat() {
        if (!checkLogin()) {
            return;
        }

        String other = otherUserField.getText().trim();
        List<String> response = sendCommand("CREATE_CHAT " + loggedInUser + "|" + other);
        printResponse(response);

        for (String line : response) {
            if (line.startsWith("CHAT_ID|")) {
                chatIdField.setText(line.substring(8));
                refreshChatView(true);
            }
        }
    }

    private void listChats() {
        if (!checkLogin()) {
            return;
        }
        printResponse(sendCommand("LIST_CHATS " + loggedInUser));
    }

    private void switchToGlobalChat() {
        if (!checkLogin()) {
            return;
        }
        chatIdField.setText(String.valueOf(GLOBAL_CHAT_ID));
        log("global chat aktiv chat-id 99");
        refreshChatView(true);
    }

    private void sendMessage() {
        if (!checkLogin()) {
            return;
        }

        String chatId = chatIdField.getText().trim();
        String text = messageField.getText().trim();

        List<String> response = sendCommand("SEND_MESSAGE " + loggedInUser + "|" + chatId + "|" + text);
        printResponse(response);

        if (!response.isEmpty() && response.get(0).startsWith("OK|")) {
            messageField.setText("");
            refreshChatView(true);
        }
    }

    private void refreshChatView(boolean showErrors) {
        if (loggedInUser == null || loggedInUser.isBlank()) {
            return;
        }

        String chatId = chatIdField.getText().trim();
        if (chatId.isEmpty()) {
            return;
        }

        List<String> response = sendCommand("GET_MESSAGES " + loggedInUser + "|" + chatId);
        if (response.isEmpty()) {
            return;
        }

        if (response.get(0).startsWith("ERROR|")) {
            if (showErrors) {
                printResponse(response);
            }
            return;
        }

        StringBuilder builder = new StringBuilder();
        String lastSender = "";

        for (String line : response) {
            if (!line.startsWith("MSG|")) {
                continue;
            }

            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) {
                continue;
            }

            String sender = parts[1];
            String text = parts[2];

            if (!lastSender.isEmpty() && !lastSender.equals(sender)) {
                builder.append("\n");
            }

            builder.append(sender).append(": ").append(text).append("\n");
            lastSender = sender;
        }

        String newRender = builder.toString();
        if (!newRender.equals(lastChatRender)) {
            chatArea.setText(newRender);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            lastChatRender = newRender;
        }
    }

    private boolean checkLogin() {
        if (loggedInUser == null || loggedInUser.isBlank()) {
            log("bitte erst einloggen");
            return false;
        }
        return true;
    }

    private List<String> sendCommand(String command) {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            List<String> error = new ArrayList<>();
            error.add("ERROR|ungueltiger port");
            return error;
        }

        try (
            Socket socket = new Socket(host, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
        ) {
            out.println(command);
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                if ("END".equals(line)) {
                    break;
                }
                lines.add(line);
            }
            return lines;
        } catch (IOException e) {
            List<String> error = new ArrayList<>();
            error.add("ERROR|keine verbindung zum server: " + e.getMessage());
            return error;
        }
    }

    private void printResponse(List<String> response) {
        if (response.isEmpty()) {
            log("keine antwort vom server");
            return;
        }
        for (String line : response) {
            log(line);
        }
    }

    private void showConnect() {
        frame.setContentPane(connectPanel);
        frame.setExtendedState(JFrame.NORMAL);
        frame.setResizable(false);
        Dimension small = new Dimension(430, 250);
        frame.setMinimumSize(small);
        frame.setMaximumSize(small);
        frame.setSize(small);
        frame.validate();
        frame.setLocationRelativeTo(null);
    }

    private void showAuth() {
        frame.setContentPane(authPanel);
        frame.setExtendedState(JFrame.NORMAL);
        frame.setResizable(false);
        Dimension small = new Dimension(430, 270);
        frame.setMinimumSize(small);
        frame.setMaximumSize(small);
        frame.setSize(small);
        frame.validate();
        frame.setLocationRelativeTo(null);
    }

    private void showMain() {
        if (mainPanel == null) {
            mainPanel = buildMainPanel();
        }

        frame.setContentPane(mainPanel);
        frame.setExtendedState(JFrame.NORMAL);
        frame.setResizable(true);
        frame.setMinimumSize(new Dimension(900, 650));
        frame.setMaximumSize(new Dimension(10000, 10000));
        frame.setSize(900, 650);
        frame.validate();
        frame.setLocationRelativeTo(null);
        log("eingeloggt als " + loggedInUser);
    }

    private void logout() {
        loggedInUser = null;
        lastChatRender = "";
        chatArea.setText("");
        outputArea.setText("");
        authStatus.setText(" ");
        showAuth();
    }

    private void setConnectStatus(String text, boolean success) {
        connectStatus.setText(text);
        connectStatus.setForeground(success ? OK : ERROR);
    }

    private void setAuthStatus(String text, boolean success) {
        authStatus.setText(text);
        authStatus.setForeground(success ? OK : ERROR);
    }

    private JButton makeButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        button.setBackground(INPUT_BG);
        button.setForeground(FG);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(FG));
        return button;
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(FG);
        return label;
    }

    private void styleInput(JTextField field, int columns) {
        field.setColumns(columns);
        field.setBackground(INPUT_BG);
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setBorder(BorderFactory.createLineBorder(FG));
    }

    private void styleArea(JTextArea area) {
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBackground(INPUT_BG);
        area.setForeground(FG);
        area.setCaretColor(FG);
    }

    private void stylePanel(JPanel panel, String title) {
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(FG), title));
    }

    private void log(String text) {
        outputArea.append(text + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }
}
