package ui;

import nio.ChatClient;
import javax.swing.*;

public class AuthWindow extends JFrame {
    private final JTextField nameField;
    private final JButton enterButton;
    private final ChatClient client;

    public AuthWindow(ChatClient client) {
        this.client = client;
        setTitle("введите имя");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 180);
        setLocationRelativeTo(null);
        setResizable(false);

        nameField = new JTextField(20);
        enterButton = new JButton("войти");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel("ваше имя:"));
        panel.add(nameField);
        panel.add(enterButton);

        // добавляем панель в окно
        setContentPane(panel);

        // обработчик кнопки
        enterButton.addActionListener(e -> onEnter());
    }

    private void onEnter() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "введите имя", "ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean success = client.authenticate(name);
        if (success) {
            dispose();
            client.showChatWindow();
        } else {
            JOptionPane.showMessageDialog(this, "не удалось зарегистрироваться", "ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}