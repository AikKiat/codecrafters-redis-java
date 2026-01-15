

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
                        String input = new String(buffer.array(), 0, bytesRead); //constructs the new string by decoding the byte array, from start index to length of byte array through bytesRead
                        System.out.println("Received:" + input);
                        String[] respInputParts = input.split("\r\n");

                        //FORMAT OF RESP is this:
                        //*<length of array>\r\n
                        //$<length of first string part>\r\n
                        //<first string part>\r\n --> the Command. Can be SET, GET, PING, ECHO, etc
                        //$<length of input value>\r\n
                        //<input value itself>

                        //ECHO case
                        if (respInputParts.length >= 5 && respInputParts[2].equalsIgnoreCase("ECHO")){
                            String inputToEcho = respInputParts[4];
                            String output = String.format("$%d\r\n%s\r\n", inputToEcho.length(), inputToEcho);
                            client.write(ByteBuffer.wrap(output.getBytes()));                            
                        }

                        else if (respInputParts.length == 3 && respInputParts[2].equalsIgnoreCase("PING")){
                            client.write(ByteBuffer.wrap("+PONG\r\n".getBytes()));
                        }
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