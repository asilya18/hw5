import java.nio.charset.StandardCharsets;

public class SocketMessage {
    private final byte[] messageLength;
    private final byte[] messageData;

    // для отправки сообщений
    public SocketMessage(String data){
        this.messageData = data.getBytes(StandardCharsets.UTF_8); // кодируем
        int dataLength = messageData.length; // определеяем длину по байтам
        this.messageLength = new byte[2];
        messageLength[0] = (byte) (dataLength); // младший байт
        messageLength[1] = (byte) (dataLength >> 8); // старший байт
    }

    // для приема сообщений
    public SocketMessage(byte[] messageLength, byte[] messageData){
        this.messageLength = messageLength;
        this.messageData = messageData;
    }

    public String getStringData(){
        return new String(messageData, StandardCharsets.UTF_8);
    }

    public byte[] getMessageLength(){
        return messageLength;
    }

    public byte[] getMessageData(){
        return messageData;
    }

}
