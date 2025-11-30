import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {
    static List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final int MAX_CLIENTS = 10;

    public static void main(String[] args) {
        System.out.println("запуск сервера...");
        int port = 0;

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        try {
            while (true) {
                System.out.println("введите порт сервера: ");
                try {
                    port = Integer.parseInt(reader.readLine());
                    break; // если порт корректный
                } catch (NumberFormatException e) {
                    System.out.println("введите корректный номер порта.");
                }
            }

            ServerSocket serverSocket = null;
            while (serverSocket == null) {
                try {
                    serverSocket = new ServerSocket(port);
                    System.out.println("сервер запущен на " + InetAddress.getLocalHost().getHostAddress() + ":" + port);
                } catch (IOException e) {
                    System.out.println("порт " + port + " занят. выберите другой порт.");

                    // запрашиваем порт еще раз в случае ошибки
                    try {
                        System.out.println("введите порт сервера: ");
                        port = Integer.parseInt(reader.readLine());
                    } catch (Exception ex) {
                        System.out.println("неверный формат порта.");
                    }
                }
            }

            while (true) {
                Socket socket = serverSocket.accept(); // сервер ждет нового клиента

                if (clients.size() > MAX_CLIENTS) {
                    System.out.println("превышен лимит клиентов. отклонено подключение.");
                    socket.close();
                    continue; // clientHandler не создается
                }

                try {
                    ClientHandler clientHandler = new ClientHandler(socket);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start(); // запускаем в отдельном потоке
                    System.out.println("новое подключение. всего клиентов: " + clients.size());
                } catch (IOException e) {
                    System.out.println("ошибка создания обработчика: " + e.getMessage());
                    socket.close();
                }
            }
        } catch (IOException e) {
            System.out.println("ошибка сервера: " + e.getMessage());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                System.out.println("ошибка при закрытии reader: " + e.getMessage());
            }
        }
    }
}