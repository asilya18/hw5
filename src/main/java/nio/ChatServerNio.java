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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChatServerNio {
    // имя клиента - канал
    private static final Map<SocketChannel, String> clientNames = new HashMap<>();
    // комната - множество каналов в этой комнате
    private static final Map<String, Set<SocketChannel>> rooms = new HashMap<>();
    // канал - текущая комната клиента
    private static final Map<SocketChannel, String> clientRooms = new HashMap<>();
    private static final int MAX_CLIENTS = 50;

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

            if (clientNames.size() > MAX_CLIENTS) {
                System.out.println("превышен лимит клиентов. подключение отклонено");
                socketChannel.close();
                return;
            }

            socketChannel.configureBlocking(false);

            // регистрируем канал в селекторе,
            // указываем, что клиентский канал должен читать данные от клиента
            socketChannel.register(selector, SelectionKey.OP_READ);

            sendMessage(socketChannel, "SYSTEM:введите ваше имя:");
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

                // парсим протокол сообщений от клиента
                if (text.contains(":")) {
                    String[] parts = text.split(":", 2);
                    String type = parts[0];
                    String data = parts.length > 1 ? parts[1] : "";

                    switch (type) {
                        case "NAME":
                            handleName(clientChannel, data, selector);
                            break;
                        case "CREATE":
                            handleCreate(clientChannel, data, selector);
                            break;
                        case "JOIN":
                            handleJoin(clientChannel, data, selector);
                            break;
                        case "LEAVE":
                            handleLeave(clientChannel, selector);
                            break;
                        case "MESSAGE":
                            handleMessage(clientChannel, data, selector);
                            break;
                        case "LIST":
                            handleList(clientChannel);
                            break;
                        default:
                            sendMessage(clientChannel, "SYSTEM: неизвестная команда");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("ошибка при обработке сообщения: " + e.getMessage());
            disconnectClient(clientChannel, selector);
        }
    }

    private static void handleName(SocketChannel clientChannel, String name, Selector selector) {
        if (clientNames.containsKey(clientChannel)) {
            sendMessage(clientChannel, "SYSTEM: вы уже зарегистрированы");
            return;
        }
        clientNames.put(clientChannel, name);
        System.out.println(name + " подключился");
        // отправляем список комнат при регистрации (будет пустым, если нет комнат)
        sendRoomList(clientChannel);
    }

    private static void handleCreate(SocketChannel clientChannel, String roomName, Selector selector) {
        if (!clientNames.containsKey(clientChannel)) {
            sendMessage(clientChannel, "SYSTEM: сначала зарегистрируйтесь");
            return;
        }

        if (rooms.containsKey(roomName)) {
            sendMessage(clientChannel, "SYSTEM: комната уже существует");
            return;
        }

        rooms.put(roomName, new HashSet<>());
        String clientName = clientNames.get(clientChannel);
        System.out.println("создана комната: " + roomName + " от " + clientName);

        sendMessage(clientChannel, "SYSTEM: вы создали комнату: " + roomName);
        sendRoomList(clientChannel);
    }

    private static void handleJoin(SocketChannel clientChannel, String roomName, Selector selector) {
        if (!clientNames.containsKey(clientChannel)) {
            sendMessage(clientChannel, "SYSTEM: cначала зарегистрируйтесь");
            return;
        }

        if (!rooms.containsKey(roomName)) {
            sendMessage(clientChannel, "SYSTEM: комната не существует");
            return;
        }

        // покидаем старую комнату если была
        handleLeave(clientChannel, selector);

        // входим в новую
        rooms.get(roomName).add(clientChannel);
        clientRooms.put(clientChannel, roomName);

        String clientName = clientNames.get(clientChannel);
        broadcastToRoom("SYSTEM:" + clientName + " присоединился к комнате", roomName, clientChannel, selector);
        sendMessage(clientChannel, "SYSTEM: вы в комнате: " + roomName);
        System.out.println(clientName + " вошел в комнату: " + roomName);
    }

    private static void handleLeave(SocketChannel clientChannel, Selector selector) {
        if (clientRooms.containsKey(clientChannel)) {
            String roomName = clientRooms.get(clientChannel);
            String clientName = clientNames.get(clientChannel);

            rooms.get(roomName).remove(clientChannel);
            clientRooms.remove(clientChannel);

            broadcastToRoom("SYSTEM:" + clientName + " покинул комнату", roomName, null, selector);
            System.out.println(clientName + " покинул комнату: " + roomName);
        }
    }

    private static void handleMessage(SocketChannel clientChannel, String message, Selector selector) {
        if (!clientNames.containsKey(clientChannel)) {
            sendMessage(clientChannel, "SYSTEM: сначала зарегистрируйтесь");
            return;
        }

        String clientName = clientNames.get(clientChannel);
        String roomName = clientRooms.get(clientChannel);

        if (roomName == null) {
            sendMessage(clientChannel, "SYSTEM: сначала войдите в комнату");
            return;
        }

        if (message.isEmpty()) {
            return;
        }

        if (message.getBytes(StandardCharsets.UTF_8).length > 1024) {
            sendMessage(clientChannel, "SYSTEM: сообщение слишком длинное");
            return;
        }

        String formatted = "MESSAGE:[" + clientName + "] " + message;
        broadcastToRoom(formatted, roomName, clientChannel, selector);
        System.out.println("[" + roomName + "] " + clientName + ": " + message);
    }

    private static void handleList(SocketChannel clientChannel) {
        sendRoomList(clientChannel);
    }


    private static void sendRoomList(SocketChannel clientChannel) {
        StringBuilder list = new StringBuilder("LIST:");
        for (String roomName : rooms.keySet()) {
            int count = rooms.get(roomName).size(); // кол-во клиентов в комнате
            list.append(roomName).append("(").append(count).append(");");
        }
        sendMessage(clientChannel, list.toString());
    }

    private static void sendMessage(SocketChannel clientChannel, String message) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
            clientChannel.write(byteBuffer);
        } catch (IOException e) {
            System.out.println("ошибка при отправке сообщения: " + e.getMessage());
        }
    }

    private static void broadcastToRoom(String message, String roomName, SocketChannel sender, Selector selector) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
            Set<SocketChannel> roomClients = rooms.get(roomName);

            if (roomClients != null) {
                for (SocketChannel client : roomClients) {
                    if (client != sender && client.isOpen() && client.isConnected()) {
                        try {
                            client.write(byteBuffer.duplicate());
                        } catch (IOException e) {
                            System.out.println("не удалось отправить сообщение клиенту: " + e.getMessage());
                        }
                    }
                }
            }
            // отправляем отправителю тоже (кроме системных сообщений)
            if (sender != null && !message.startsWith("SYSTEM:") && sender.isOpen() && sender.isConnected()) {
                sender.write(byteBuffer.duplicate());
            }
        } catch (Exception e) {
            System.out.println("ошибка при рассылке сообщения: " + e.getMessage());
        }
    }

    private static void disconnectClient(SocketChannel clientChannel, Selector selector) {
        String clientName = clientNames.get(clientChannel);
        // покидаем комнату
        handleLeave(clientChannel, selector);
        clientNames.remove(clientChannel);
        clientRooms.remove(clientChannel);
        try {
            if (clientChannel.isOpen()) {
                clientChannel.close();
            }
        } catch (IOException e) {
            System.out.println("ошибка при закрытии канала: " + e.getMessage());
        }

        if (clientName != null) {
            System.out.println(clientName + " отсоединился");
        }
    }
}