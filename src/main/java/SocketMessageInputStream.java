import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketMessageInputStream extends InputStream {
    private final InputStream inputStream;

    public SocketMessageInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    public SocketMessage readSocketMessage() throws IOException {
        byte[] lengthBytes = new byte[2];
        if (inputStream.read(lengthBytes) == -1) { // читаем два байта заголовка
            throw new EOFException();
        }
        int messageLength = (lengthBytes[0] & 0xFF) + ((lengthBytes[1] & 0xFF) << 8); // считаем длину сообщения
        byte[] dataBytes = new byte[messageLength];
        int readedBytes = inputStream.read(dataBytes); // читаем содержание сообщения
        if (readedBytes < messageLength){
            System.out.println(new String(dataBytes, StandardCharsets.UTF_8));
            throw new EOFException("readed %d bytes insted of %d".formatted(readedBytes, messageLength));
        }
        return new SocketMessage(lengthBytes, dataBytes);
    }
}
