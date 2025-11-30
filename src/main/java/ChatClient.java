import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ChatClient {
    private Socket socket;
    private SocketMessageInputStream inputStream;
    private SocketMessageOutputStream outputStream;
    private String clientName;
    private boolean connected = false;

    public static void main(String[] args) {
        new ChatClient().start();
    }

    public void start(){
        System.out.println("запускаем клиент");

        if (!connectToServer()){
            System.out.println("не удалось подключиться");
            return;
        }

        if (!authenticate()){
            System.out.println("не удалось зарегистрироваться");
            return;
        }

        System.out.println("теперь можно общаться в чате!");

        startMessageReader();
        startMessageWriter();
    }

    private boolean connectToServer(){
        System.out.println("подключаемся к серверу...");

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("введите адрес сервера: ");
            String address = reader.readLine();

            // пока не введут корректный порт
            while (true) {
                System.out.println("введите порт сервера: ");
                try {
                    int port = Integer.parseInt(reader.readLine());

                    try {
                        socket = new Socket(address, port);
                        inputStream = new SocketMessageInputStream(socket.getInputStream());
                        outputStream = new SocketMessageOutputStream(socket.getOutputStream());

                        connected = true;
                        System.out.println("вы подключились к серверу");
                        return true;

                    } catch (IOException e) {
                        System.out.println("не удалось подключиться: " + e.getMessage());
                        System.out.println("попробуйте другой порт:");
                    }

                } catch (NumberFormatException e) {
                    System.out.println("неверный формат порта. введите число:");
                }
            }

        } catch (IOException e) {
            System.out.println("ошибка ввода: " + e.getMessage());
            return false;
        }
    }

    private boolean authenticate(){
        System.out.println("регистрируемся в чате...");
        try {
            SocketMessage prompt = inputStream.readSocketMessage();
            // читаем сообщение от сервера с запросом имени
            System.out.println(prompt.getStringData());

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            clientName = reader.readLine();

            outputStream.writeSocketMessage(new SocketMessage(clientName));
            // отправляем сообщение с именем на сервер

            System.out.println(clientName + ", вы в чате");
            return true;

        } catch (IOException e) {
            System.out.println("ошибка при регистрации: " + e.getMessage());
            return false;
        }
    }

    private void startMessageReader(){
        Thread readerThread = new Thread(() -> {
            try {
                while (connected) {
                    SocketMessage message = inputStream.readSocketMessage();
                    System.out.println(message.getStringData());
                }
            } catch (IOException e) {
                if (connected) {
                    System.out.println("соединение с сервером разорвано");
                    connected = false;
                }
            }
        });

        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void startMessageWriter(){
        System.out.println("напишите сообщение и нажмите enter для отправки. для выхода напишите exit");

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (connected) {
                String input = reader.readLine();

                if (!connected) {
                    break;
                }

                if (input == null || input.equalsIgnoreCase("exit")) {
                    outputStream.writeSocketMessage(new SocketMessage("exit"));
                    break;
                }

                outputStream.writeSocketMessage(new SocketMessage(input));
            }

        } catch (IOException e) {
            if (connected) {
                System.out.println("ошибка при отправке сообщения: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void disconnect() {
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
}
