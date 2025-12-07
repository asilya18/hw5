import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final SocketMessageInputStream inputStream;
    private final SocketMessageOutputStream outputStream;
    private String clientName;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = new SocketMessageInputStream(socket.getInputStream());
        this.outputStream = new SocketMessageOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        try {
            send(new SocketMessage("введите ваше имя:"));
            SocketMessage nameMessage = inputStream.readSocketMessage();
            clientName = nameMessage.getStringData();

            broadcast(clientName + " присоединился к чату");
            System.out.println(clientName + " подключился");

            // обработка сообщений
            while (true) {
                SocketMessage message = inputStream.readSocketMessage();
                String text = message.getStringData();

                if (text == null || text.equalsIgnoreCase("exit")) {
                    break;
                }

                if (text.trim().isEmpty()) {
                    continue; // пропускаем пустое сообщение
                }

                if (text.getBytes(StandardCharsets.UTF_8).length > 256) {
                    send(new SocketMessage("ошибка: слишком длинное сообщение"));
                    continue; // не рассылаем сообщение такой длины
                }

                String formatted = "[" + clientName + "] " + text;
                broadcast(formatted);
                System.out.println(formatted);
            }
        } catch (IOException e) {
            System.err.println("ошибка с клиентом " + clientName + ": " + e.getMessage());
        } finally {
            try {
                ChatServer.clients.remove(this);

                if (clientName != null) {
                    broadcast(clientName + " покинул чат");
                    System.out.println(clientName + " отсоединился");
                }
                socket.close();
            } catch (IOException e) {
                System.out.println("ошибка при закрытии соединения: " + e.getMessage());
            }
        }
    }

    private void send(SocketMessage message) {
        try {
            outputStream.writeSocketMessage(message); // отправка сообщения конкретному клиенту
            System.out.println("сообщение отправлено клиенту [" + clientName + "]: "
                    + message.getStringData());
        } catch (IOException e) {
            System.err.println("ошибка отправки сообщения клиенту [" + clientName + "]: "
                    + e.getMessage());
        }
    }

    private void broadcast(String text){
        SocketMessage message = new SocketMessage(text);
        for (ClientHandler client : ChatServer.clients) {
            client.send(message);
        }
    }
}
