package ui;

import nio.ChatClient;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class RoomWindow extends JFrame implements ChatClient.MessageListener {
    private final ChatClient client;
    private final JList<String> roomList;
    private final DefaultListModel<String> roomListModel;
    private final JButton joinButton;
    private final JButton createButton;
    private final JButton refreshButton;
    private final JTextField createField;

    public RoomWindow(ChatClient client) {
        this.client = client;
        // регистрируем себя как слушателя
        this.client.addMessageListener(this);

        setTitle("выбор комнаты");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null);
        setResizable(false);

        // модель для списка комнат
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel); // визуальный компонент модели
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(roomList);

        // кнопки
        joinButton = new JButton("присоединиться");
        createButton = new JButton("создать");
        refreshButton = new JButton("обновить");

        // поле для создания комнаты
        createField = new JTextField(20);

        // панель для создания
        JPanel createPanel = new JPanel(new BorderLayout());
        createPanel.add(new JLabel("название комнаты:"), BorderLayout.WEST);
        createPanel.add(createField, BorderLayout.CENTER);
        createPanel.add(createButton, BorderLayout.EAST);

        // панель для кнопок
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(joinButton);
        buttonPanel.add(refreshButton);

        // главная панель
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(new JLabel("доступные комнаты:"), BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // панель создания внизу
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(createPanel, BorderLayout.CENTER);

        // компоновка окна
        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // обработчики
        joinButton.addActionListener(e -> joinRoom());
        createButton.addActionListener(e -> createRoom());
        refreshButton.addActionListener(e -> refreshRooms());
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // выбор завершен
                joinButton.setEnabled(roomList.getSelectedIndex() != -1); // выбрана ли какая-то комната
                // если да, включается кнопка "присоединиться"
            }
        });
        setVisible(true);
        // запрашиваем список комнат
        SwingUtilities.invokeLater(() -> {
            client.requestRoomList();
        });
    }

    @Override
    public void onMessageReceived(String message) {
        if (message.startsWith("вы создали комнату:")) {
            // автоматически входим в созданную комнату
            String roomName = message.substring("вы создали комнату: ".length()).trim();
            SwingUtilities.invokeLater(() -> {
                enterRoom(roomName);
            });
        }
    }

    @Override
    public void onConnectionLost(String reason) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this,
                    "соединение с сервером потеряно: " + reason,
                    "ошибка",
                    JOptionPane.ERROR_MESSAGE);
            dispose();
        });
    }

    @Override
    public void onRoomListReceived(List<String> rooms) {
        SwingUtilities.invokeLater(() -> {
            roomListModel.clear(); // очищаем старый список
            for (String room : rooms) {
                roomListModel.addElement(room); // добавляем элементы с сервера
            }
            if (roomListModel.size() > 0) { // по умолчанию
                roomList.setSelectedIndex(0); // выбор стоит на первой комнате в списке
                joinButton.setEnabled(true);
            } else {
                joinButton.setEnabled(false);
            }
        });
    }

    private void joinRoom() {
        String selected = roomList.getSelectedValue(); // в списке отображается как комната(число)
        if (selected != null) {
            // убираем количество участников из отображения
            String roomName = selected.split("\\(")[0].trim(); // берем только название от чата
            enterRoom(roomName);
        }
    }

    private void enterRoom(String roomName) {
        client.joinRoom(roomName);
        // удаляем себя из слушателей перед переходом в чат
        client.removeMessageListener(this);
        dispose();
        client.showChatWindow();
    }

    private void createRoom() {
        String roomName = createField.getText().trim();
        if (roomName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "введите название комнаты",
                    "ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        client.createRoom(roomName);
        createField.setText("");
    }

    private void refreshRooms() {
        client.requestRoomList();
    }

    @Override
    public void dispose() {
        // при закрытии окна удаляем себя из слушателей
        client.removeMessageListener(this);
        super.dispose();
    }
}