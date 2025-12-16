package nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ChatServerNio {
    private static final Map<SocketChannel, String> clients = new HashMap<>();
    private static final int MAX_CLIENTS = 10;

    public static void main(String[] args) {
        try {
            System.out.println("запуск сервера...");
            int port = askPort();

            // открываем серверный канал
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(port));

            // ставим неблокирующий режим
            serverSocketChannel.configureBlocking(false);

            Selector selector = Selector.open();

            // регистрируем серверный канал на событие ACCEPT
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("сервер запущен на порту " + port);

            while (true) {
                try {
                    // метод select обработает каждый канал,
                    // где произошло какое-то событие,
                    // то есть селектор вызовет метод handleSelection
                    selector.select(key -> handleConnection(key, selector));
                } catch (IOException e) {
                    System.out.println("ошибка при работе селектора: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("ошибка запуска сервера: " + e.getMessage());
        }
    }

    private static int askPort() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        int port = 0;

        while (true) {
            try {
                System.out.println("введите порт сервера");
                port = Integer.parseInt(reader.readLine());
                break;
            } catch (Exception e) {
                System.out.println("введите корректный номер порта");
            }
        }
        return port;
    }

    private static void handleConnection(SelectionKey selectionKey, Selector selector) {
        // selectionKey — объект, который описывает канал и событие
        if (selectionKey.isAcceptable()) {
            // если на серверном канале есть клиент, готовый подключиться,
            // достаем из selectionKey серверный канал
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();

            // подключаем клиента к серверному каналу
            registerNewChannel(selector, serverSocketChannel);
        }

        // если на клиентском канале есть данные для чтения
        if (selectionKey.isReadable()) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            processMessage(socketChannel, selector);
        }
    }

    private static void registerNewChannel(Selector selector, ServerSocketChannel serverSocketChannel) {
        try {
            // принимаем новый клиентский канал
            SocketChannel socketChannel = serverSocketChannel.accept();

            if (clients.size() > MAX_CLIENTS) {
                System.out.println("превышен лимит клиентов. подключение отклонено");
                socketChannel.close();
                return;
            }

            socketChannel.configureBlocking(false);

            // регистрируем канал в селекторе,
            // указываем, что клиентский канал должен читать данные от клиента
            socketChannel.register(selector, SelectionKey.OP_READ);

            sendMessage(socketChannel, "введите ваше имя: ");
            System.out.println("новое подключение: " + socketChannel.getRemoteAddress());
        } catch (IOException e) {
            System.out.println("ошибка при принятии соединения: " + e.getMessage());
        }
    }

    private static void processMessage(SocketChannel clientChannel, Selector selector) {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        try {
            int readBytes = clientChannel.read(buffer);

            if (readBytes == -1) {
                disconnectClient(clientChannel, selector);
                return;
            }

            if (readBytes > 0) {
                buffer.flip();
                byte[] bytes = new byte[readBytes];
                buffer.get(bytes);
                String text = new String(bytes, StandardCharsets.UTF_8).trim();

                // если клиента ещё нет в списке — это его имя
                if (!clients.containsKey(clientChannel)) {
                    clients.put(clientChannel, text);
                    broadcast(text + " присоединился к чату", clientChannel, selector);
                    System.out.println(text + " подключился");
                    return;
                }

                String clientName = clients.get(clientChannel);

                if (text.equalsIgnoreCase("exit")) {
                    disconnectClient(clientChannel, selector);
                    return;
                }

                if (text.isEmpty()) {
                    return;
                }

                if (text.getBytes(StandardCharsets.UTF_8).length > 1024) {
                    sendMessage(clientChannel, "ошибка: сообщение слишком длинное");
                    return;
                }

                String formatted = "[" + clientName + "] " + text;
                broadcast(formatted, clientChannel, selector);
                System.out.println(formatted);
            }
        } catch (IOException e) {
            System.out.println("ошибка при обработке сообщения: " + e.getMessage());
            try {
                clientChannel.close();
            } catch (IOException ex) {
                System.out.println("ошибка при закрытии канала клиента: " + ex.getMessage());
            }
        }
    }

    private static void sendMessage(SocketChannel clientChannel, String message) {
        try {
            // метод wrap() в байтовом виде заворачивает в буфер сообщение
            // добавляем перевод строки, чтобы клиент мог считать через readLine()
            ByteBuffer byteBuffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));

            // записываем сообщение в канал из буфера
            clientChannel.write(byteBuffer);
        } catch (IOException e) {
            System.out.println("ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    private static void broadcast(String message, SocketChannel sender, Selector selector) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
            // перебираем ключи, каждый ключ соответствует зарегистрированному каналу
            for (SelectionKey key : selector.keys()) {
                // пропускаем невалидные ключи и серверный канал
                if (!key.isValid() || !(key.channel() instanceof SocketChannel)) {
                    continue;
                }

                SocketChannel client = (SocketChannel) key.channel();

                if (client.isOpen() && client.isConnected()) {
                    try {
                        // duplicate() нужен чтобы все клиенты получили сообщение,
                        // иначе после первой записи position = limit
                        client.write(byteBuffer.duplicate());
                    } catch (IOException e) {
                        System.out.println("не удалось отправить сообщение клиенту: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("ошибка при рассылке сообщения: " + e.getMessage());
        }
    }

    private static void disconnectClient(SocketChannel clientChannel, Selector selector) {
        String clientName = clients.get(clientChannel);
        clients.remove(clientChannel);

        try {
            if (clientChannel.isOpen()) {
                clientChannel.close();
            }
        } catch (IOException e) {
            System.out.println("ошибка при закрытии канала: " + e.getMessage());
        }

        if (clientName != null) {
            broadcast(clientName + " покинул чат", clientChannel, selector);
            System.out.println(clientName + " отсоединился");
        }
    }
}