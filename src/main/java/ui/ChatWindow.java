package ui;

import nio.ChatClient;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.List;

public class ChatWindow extends JFrame implements ChatClient.MessageListener {
    private final ChatClient client;
    private final JTextPane chatPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton exitButton;
    private String roomName; // храним название комнаты

    public ChatWindow(ChatClient client) {
        this(client, ""); // по умолчанию без названия комнаты
    }

    public ChatWindow(ChatClient client, String roomName) {
        this.client = client;
        this.roomName = roomName;
        // регистрируем это окно как слушателя сообщений
        this.client.addMessageListener(this);

        // устанавливаем заголовок с названием комнаты
        setTitle("чатик" + (roomName != null && !roomName.isEmpty() ? " - " + roomName : ""));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // область сообщений
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatPane);

        inputField = new JTextField();
        sendButton = new JButton("отправить");
        exitButton = new JButton("выход");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(sendButton);
        buttonPanel.add(exitButton);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        // обработчики кнопок
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage()); // отправка по enter
        exitButton.addActionListener(e -> exitChat());
    }

    @Override
    public void onMessageReceived(String message) {
        // обновляем UI в потоке Swing
        SwingUtilities.invokeLater(() -> appendMessage(message));
    }

    @Override
    public void onConnectionLost(String reason) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, reason, "соединение потеряно",
                    JOptionPane.WARNING_MESSAGE);
            exitChat();
        });
    }

    // ДОБАВЛЯЕМ ЭТОТ МЕТОД - он требуется по интерфейсу MessageListener
    @Override
    public void onRoomListReceived(List<String> rooms) {
        // этот метод не используется в окне чата,
        // но мы должны его реализовать по контракту интерфейса
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.isEmpty()) {
            client.sendMessage(message);
            inputField.setText("");
        }
    }

    private void exitChat() {
        // удаляем себя из слушателей перед выходом
        client.removeMessageListener(this);
        client.sendMessage("exit");
        client.disconnect();
        dispose();
    }

    private void appendMessage(String message) {
        StyledDocument document = chatPane.getStyledDocument();

        // создаем стиль для имени
        Style nameStyle = chatPane.getStyle("NameStyle");
        if (nameStyle == null) {
            nameStyle = chatPane.addStyle("NameStyle", null);
            StyleConstants.setForeground(nameStyle, Color.PINK);
            StyleConstants.setBold(nameStyle, true);
        }

        // создаем стиль для сообщений
        Style msgStyle = chatPane.getStyle("MsgStyle");
        if (msgStyle == null) {
            msgStyle = chatPane.addStyle("MsgStyle", null);
            StyleConstants.setForeground(msgStyle, Color.BLACK);
        }

        // создаем стиль для системных сообщений
        Style systemStyle = chatPane.getStyle("SystemStyle");
        if (systemStyle == null) {
            systemStyle = chatPane.addStyle("SystemStyle", null);
            StyleConstants.setForeground(systemStyle, Color.GRAY);
            StyleConstants.setItalic(systemStyle, true);
        }

        // проверяем на системное сообщение (без имени в квадратных скобках)
        if (!message.contains("[") || message.indexOf("]") <= 0) {
            // системное сообщение
            try {
                document.insertString(document.getLength(), message + "\n", systemStyle);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        } else {
            // обычное сообщение с именем
            int closingBracketIndex = message.indexOf("]");
            if (message.startsWith("[") && closingBracketIndex > 0) {
                String nameTag = message.substring(0, closingBracketIndex + 1); // [имя]
                String msg = message.substring(closingBracketIndex + 1).trim();

                try {
                    document.insertString(document.getLength(), nameTag + " ", nameStyle);
                    document.insertString(document.getLength(), msg + "\n", msgStyle);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            } else {
                // сообщение без формата
                try {
                    document.insertString(document.getLength(), message + "\n", msgStyle);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        }

        chatPane.setCaretPosition(document.getLength()); // прокрутка вниз
    }

    // метод для обновления названия комнаты (если нужно будет менять динамически)
    public void setRoomName(String roomName) {
        this.roomName = roomName;
        setTitle("чатик" + (roomName != null && !roomName.isEmpty() ? " - " + roomName : ""));
    }
}