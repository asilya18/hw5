package nio;

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
    private boolean connected = false;
    private final List<MessageListener> messageListeners = new ArrayList<>();

    // интерфейс для слушателей сообщений
    public interface MessageListener {
        void onMessageReceived(String message); // сообщение от сервера
        void onConnectionLost(String reason); // соединение с сервером прервано
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
            return true;
        } catch (IOException e) {
            System.out.println("не удалось подключиться: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticate(String name) {
        try {
            String prompt = in.readLine(); // чтение потока байтов из сокета от сервера (запрос имени)
            System.out.println(prompt);
            clientName = name;
            out.println(clientName); // отправляем имя
            System.out.println(clientName + ", вы в чате");
            return true;
        } catch (IOException e) {
            System.out.println("ошибка при регистрации: " + e.getMessage());
            return false;
        }
    }

    // слушатель серверных сообщений
    private void startMessageReader() {
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    // уведомляем всех слушателей о новом сообщении
                    notifyMessageReceived(line);
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
            out.println(text);
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
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

    public void showChatWindow() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            ui.ChatWindow chatWindow = new ui.ChatWindow(this);
            chatWindow.setVisible(true);
            startMessageReader();
        });
    }
}