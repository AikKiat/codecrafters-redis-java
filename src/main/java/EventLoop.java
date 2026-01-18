

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class EventLoop{

    private int portNumber;
    private Selector selector; //Create Selector (Epoll equivalent for redis)
    private ServerSocketChannel serverChannel;
    private Datastore databaseSingleton;


    public EventLoop(int portNumber) throws IOException{
        this.portNumber = portNumber;
        this.selector = Selector.open();

        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(portNumber));
        this.serverChannel.configureBlocking(false);

        //Register server socket for ACCEPT events
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT); //New TCP connection

        this.databaseSingleton = Datastore.getInstance();


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
                        Client client = new Client(key, 1024);
                        String input = client.read();
                        
                        if(input == null){
                            key.cancel();
                            continue;
                        }

                        String[] respInputParts = input.split("\r\n");

                        //FORMAT OF RESP BULK STRING is this:
                        //*<length of array>\r\n
                        //$<length of first string part>\r\n
                        //<first string part>\r\n --> the Command. Can be SET, GET, PING, ECHO, etc
                        //$<length of input value>\r\n
                        //<input value itself>

                        
                        if(respInputParts.length >= 3){
                            String command = respInputParts[2].toUpperCase();

                            switch(command){

                                case "ECHO":
                                    String inputToEcho = respInputParts[4];
                                    client.write(String.format("$%d\r\n%s\r\n", inputToEcho.length(), inputToEcho));  
                                    break;

                                case "PING":
                                    client.write("+PONG\r\n");
                                    break;
                                    
                                case "SET":

                                    String keyToSet = respInputParts[4];
                                    String valueToSet = respInputParts[6];
                                    Long expiryTime = -1L;
                                    if(respInputParts.length >= 10 && respInputParts[8].equalsIgnoreCase("PX")){
                                        System.out.println(respInputParts[8]);
                                        System.out.println(respInputParts[7]);
                                        expiryTime = Long.parseLong(respInputParts[10]);
                                    }
                                    this.databaseSingleton.setKeyValue(keyToSet, valueToSet, expiryTime);
                                    client.write("+OK\r\n");
                                    break;
                                
                                case "GET":

                                    String rediskey = respInputParts[4];
                                    Object redisValue = databaseSingleton.getKeyValue(rediskey);
                                    if (redisValue != null){
                                        String stringValue = String.valueOf(redisValue);
                                        client.write(String.format("$%d\r\n%s\r\n", stringValue.length(), stringValue));
                                    }
                                    else{
                                        client.write("$-1\r\n");
                                    }
                                    break;
                                
                                case "INCR":
                                    String incrKey = respInputParts[4];
                                    Integer increResult = databaseSingleton.incrementKey(incrKey);
                                    if(increResult == 1 || 0){
                                        client.write("+OK\r\n");
                                    }
                                    else{
                                        client.write("$-1\r\n");
                                    }
                                }
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