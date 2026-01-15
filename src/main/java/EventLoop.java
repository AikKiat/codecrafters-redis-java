

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class EventLoop{

    private int portNumber;
    private Selector selector; //Create Selector (Epoll equivalent for redis)
    private ServerSocketChannel serverChannel;


    public EventLoop(int portNumber) throws IOException{
        this.portNumber = portNumber;
        this.selector = Selector.open();

        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(portNumber));
        this.serverChannel.configureBlocking(false);

        //Register server socket for ACCEPT events
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT); //New TCP connection
    }

    public void clientCommunication() throws IOException{
        while(true){
           this.selector.select();

           Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
           while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                try{
                    if(key.isAcceptable()){
                        SocketChannel client = serverChannel.accept();
                        client.configureBlocking(false);
                        client.register(this.selector, SelectionKey.OP_READ); //Read data from client
                        System.out.println("Client Connected" + client);
                    }

                    if(key.isReadable()){
                        SocketChannel client = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        int bytesRead = client.read(buffer);
                        if (bytesRead == -1){
                            client.close();
                            System.out.println("Client Disconnected");
                            continue;
                        }
                        String input = new String(buffer.array(), 0, bytesRead);
                        System.out.println("Received:" + input);

                        client.write(ByteBuffer.wrap("+PONG\r\n".getBytes()));
                    }
                } catch(IOException e){
                    key.channel().close();
                    key.cancel();
                    System.out.println("Client error, connection closed");
                }
           }

        }
    }
}