package nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;
    private boolean connected = false;
    private ui.ChatWindow chatWindow;

    public static void main(String[] args) {
        ChatClient client = new ChatClient();
        client.showConnectWindow();
    }

    public void startConsole() {
        System.out.println("запускаем клиент");

        if (!askAndConnect()) {
            return;
        }

        if (!askAndAuthenticate()) {
            return;
        }

        System.out.println("теперь можно общаться в чате!");
        startMessageReader();
        startMessageWriterConsole();
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
                    // печатаем сообщение от сервера
                    showIncomingMessage(line);
                }
            } catch (IOException e) {
                if (connected) {
                    System.out.println("соединение с сервером разорвано");
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
    }

    private boolean askAndConnect() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.println("введите адрес сервера [по умолчанию: localhost]: ");
                String addressInput = reader.readLine();
                String address = addressInput.trim().isEmpty() ? "localhost" : addressInput.trim();

                System.out.println("введите порт сервера: ");
                String portInput = reader.readLine();

                try {
                    int port = Integer.parseInt(portInput);
                    if (connectToServer(address, port)) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("неверный формат порта");
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    private boolean askAndAuthenticate() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("введите ваше имя:");
            String name = reader.readLine();
            return authenticate(name);
        } catch (IOException e) {
            return false;
        }
    }

    private void startMessageWriterConsole() {
        System.out.println("напишите сообщение и нажмите enter для отправки. для выхода напишите exit");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (connected) {
                String input = reader.readLine();
                if (input == null || input.equalsIgnoreCase("exit")) {
                    sendMessage("exit");
                    break;
                }
                sendMessage(input);
            }
        } catch (IOException e) {
            System.out.println("ошибка при отправке: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void showIncomingMessage(String msg) {
        if (chatWindow != null) {
            // обновляем UI в потоке Swing
            javax.swing.SwingUtilities.invokeLater(() -> chatWindow.appendMessage(msg));
        } else {
            // если окно ещё не создано, печатаем в консоль
            System.out.println(msg);
        }
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
            chatWindow = new ui.ChatWindow(this);
            chatWindow.setVisible(true);
            startMessageReader(); // запускаем слушатель сообщений
        });
    }
}