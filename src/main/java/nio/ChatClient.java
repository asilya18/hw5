package nio;

import ui.RoomWindow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;
    private String currentRoom;
    private boolean connected = false;
    private final List<MessageListener> messageListeners = new ArrayList<>();

    // интерфейс для слушателей сообщений
    public interface MessageListener {
        void onMessageReceived(String message); // сообщение от сервера
        void onConnectionLost(String reason); // соединение с сервером прервано
        void onRoomListReceived(List<String> rooms); // получен список комнат
    }

    // методы для регистрации слушателей
    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    // уведомление слушателей о новом сообщении
    private void notifyMessageReceived(String message) {
        for (MessageListener listener : messageListeners) {
            listener.onMessageReceived(message);
        }
    }

    // уведомление слушателей о потере соединения
    private void notifyConnectionLost(String reason) {
        for (MessageListener listener : messageListeners) {
            listener.onConnectionLost(reason);
        }
    }

    // уведомление о списке комнат
    private void notifyRoomListReceived(List<String> rooms) {
        for (MessageListener listener : messageListeners) {
            listener.onRoomListReceived(rooms);
        }
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.showConnectWindow();
    }

    public boolean connectToServer(String address, int port) {
        try {
            socket = new Socket(address, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;
            System.out.println("вы подключились к серверу " + address + ":" + port);
            startMessageReader();
            String prompt = in.readLine();
            if (prompt != null && prompt.startsWith("SYSTEM:")) {
                System.out.println("cервер: " + prompt.substring(7));
            }
            return true;
        } catch (IOException e) {
            System.out.println("не удалось подключиться: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticate(String name) {
        try {
            clientName = name;
            out.println("NAME:" + clientName);
            System.out.println(clientName + ", вы зарегистрированы");
            return true;
        } catch (Exception e) {
            System.out.println("ошибка при регистрации: " + e.getMessage());
            return false;
        }
    }

    private void parseRoomList(String listStr) {
        // получаем от сервера сообщение типа
        // LIST:Комната1(2);Комната2(0);Комната3(1);
        List<String> rooms = new ArrayList<>();
        if (listStr != null && !listStr.isEmpty()) {
            String[] roomEntries = listStr.split(";");
            for (String entry : roomEntries) {
                if (!entry.trim().isEmpty()) {
                    // формируем список комнат
                    rooms.add(entry.trim());
                }
            }
        }
        notifyRoomListReceived(rooms); // передаем полученный списко слушателям
    }

    // слушатель серверных сообщений
    private void startMessageReader() {
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    System.out.println("сообщение от сервера: " + line);
                    // обрабатываем разные типы сообщений
                    if (line.startsWith("LIST:")) {
                        parseRoomList(line.substring(5));
                    } else if (line.startsWith("SYSTEM:")) {
                        // системные сообщения тоже показываем в чате (кто-то покинул/присоединился к чату)
                        notifyMessageReceived(line.substring(7));
                    } else if (line.startsWith("MESSAGE:")) {
                        notifyMessageReceived(line.substring(8));
                    } else {
                        // старый формат или другие сообщения
                        notifyMessageReceived(line);
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    notifyConnectionLost("соединение с сервером разорвано");
                    connected = false;
                }
            }
        });

        readerThread.setDaemon(true); // после прерывания этого потока весь main прерывается
        readerThread.start();
    }

    public void sendMessage(String text) {
        if (connected) {
            out.println("MESSAGE:" + text);
        }
    }

    public void createRoom(String roomName) {
        if (connected) {
            out.println("CREATE:" + roomName);
        }
    }

    public void joinRoom(String roomName) {
        if (connected) {
            out.println("JOIN:" + roomName);
            currentRoom = roomName;
        }
    }

    public void requestRoomList() {
        if (connected) {
            out.println("LIST:");
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                out.println("LEAVE:");
                socket.close();
            }
        } catch (IOException e) {
            // игнорируем ошибки при закрытии
        }
        System.out.println("вы отключились от чата. до свидания!");
        notifyConnectionLost("вы отключились от чата");
    }


    public void showAuthWindow() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            ui.AuthWindow authWindow = new ui.AuthWindow(this);
            authWindow.setVisible(true);
        });
    }

    public void showConnectWindow() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            ui.ConnectWindow connectWindow = new ui.ConnectWindow(this);
            connectWindow.setVisible(true);
        });
    }

    public void showRoomWindow() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            RoomWindow roomWindow = new RoomWindow(this);
            roomWindow.setVisible(true);
        });
    }

    public void showChatWindow() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            ui.ChatWindow chatWindow = new ui.ChatWindow(this);
            chatWindow.setVisible(true);
        });
    }
}