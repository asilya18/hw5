package ui;

import nio.ChatClient;
import javax.swing.*;

public class ConnectWindow extends JFrame {
    private final JTextField addressField;
    private final JTextField portField;
    private final JButton connectButton;
    private final ChatClient client;

    public ConnectWindow(ChatClient client) {
        this.client = client;
        setTitle("подключение к серверу");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(360, 180);
        setLocationRelativeTo(null); // окно в середине экрана
        setResizable(false);

        addressField = new JTextField("localhost", 16);
        portField = new JTextField("505", 6);
        connectButton = new JButton("подключиться");

        // панель с вертикальным расположением элементов
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // добавление элементов на панель
        panel.add(new JLabel("Адрес сервера:"));
        panel.add(addressField);
        panel.add(new JLabel("Порт:"));
        panel.add(portField);
        panel.add(connectButton);

        // добавляем панель в окно
        setContentPane(panel);

        // обработчик кнопки
        connectButton.addActionListener(e -> onConnect());
    }

    private void onConnect() {
        String address = addressField.getText().trim();
        String portText = portField.getText().trim();

        // проверка порта
        int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "введите корректный порт (1-65535)",
                    "ошибка",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // попытка подключения
        boolean success = client.connectToServer(address, port);
        if (success) {
            JOptionPane.showMessageDialog(this,
                    "успешное подключение к " + address + ":" + port,
                    "готово",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose(); // закрываем окно подключения
            client.showAuthWindow(); // открываем окно ввода имени
        } else {
            JOptionPane.showMessageDialog(this,
                    "не удалось подключиться. проверьте адрес/порт и сервер.",
                    "ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}