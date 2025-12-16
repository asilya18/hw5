package ui;

import nio.ChatClient;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

public class ChatWindow extends JFrame implements ChatClient.MessageListener {
    private final ChatClient client;
    private final JTextPane chatPane;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton exitButton;

    public ChatWindow(ChatClient client) {
        this.client = client;
        // регистрируем это окно как слушателя сообщений
        this.client.addMessageListener(this);

        setTitle("чатик");
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

    public void appendMessage(String message) {
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
            try {
                document.insertString(document.getLength(), message + "\n", msgStyle);
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }

        chatPane.setCaretPosition(document.getLength()); // прокрутка вниз
    }
}