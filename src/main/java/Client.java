
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.io.IOException;


public class Client{
    private SocketChannel client;
    private ByteBuffer buffer;

    public Client(SelectionKey key, Integer bufferSize){
        this.client = (SocketChannel) key.channel();
        this.buffer = ByteBuffer.allocate(bufferSize);
    }

    public void write(String toWrite) throws IOException{
        client.write(this.buffer.wrap(toWrite.getBytes()));  
    }

    public String read() throws IOException{
        int bytesRead = client.read(this.buffer);
        if (bytesRead == -1){
            client.close();
            System.out.println("Client Disconnected");
            return null;
        }
        buffer.flip(); // prepare for reading

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        buffer.clear(); // clear the buffer, prepare for next read

        String input = new String(data);
        System.out.println("Received from Client " + client + ": " + input);

        return input;
    }

}