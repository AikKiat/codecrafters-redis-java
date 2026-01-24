

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

    /*
    JAVA NIO (New IO) is an API for non-blocking I/O introduced in JDK 1.4, and contains key classes such as:
    ByteBuffer
    SelectionKey
    Selector
    ServerSocketChannel
    SocketChannel

    The key problem is that Socket client = server.accept() --> blocks. So, without Java NIO we would need to continually spawn new threads via:
    new Thread(() -> handle(client)).start();
    This is wasteful of resources, bad scalability and worse off, needs to check through/continually poll every single thread for updates!! Imagine 1million users -> 1 million threads?! Not feasible and scalable.
    This is where event driven IO comes in.

    Selector is a class that internally uses the epoll (linux) or kqueue(mac), thus utilising event driven IO.
    SelectionKey is a class that has key attributes defining the activity/updates of the channel (ServerSocketChannel or SocketChannel)
    Such as:
    class SelectionKey{
        Channel channel(); --> ServerSocketChannel or SocketChannel --> ServerSocket channel accepts connections, SocketChannel is to read/write bytes.
        int InterestOps(); --> What I want to be notified about 
        int readyOps(); --> Flag for whether the particular channel is ready, via which operations are currently possible without blocking
        Object attachement(); --> The object attached to this SelectionKey class that will be called to perform operations.
    }
    Some examples of InterstOps (what is this SelectionKey interested about? To signal an event update?)
    SelectionKey.OP_ACCEPT  // server socket only
    SelectionKey.OP_READ  
    SelectionKey.OP_WRITE

    channel.register(selector, OP_READ); --> means wake up the thread when the channel has registered reads without blocking.

    key.isReadable() == true --> Means that the SelectionKey is readable, and thus we proceed to extract what can be read from the channel encapsulated within it!!! Because it was bound with the OP_READ interest operation.

    Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator(); --> So, this is basically an iterator generated from all of the current SelectionKeys (snapshot) that are ready at one point in time to be processed,
    and of course we need to remove them from the iterator after attending to them else they will keep firing. --> keys.remove();

    So, the "attachment" specified earlier was basically the Client class instance that we create, for each socket channel connection
    So every socket channel --> one client --> and this hence preserves the statefulness of the client's attributes pertaining to this socket channel, represented by the SelectionKey class that signifies readiness based off updates from the socket channel.
    */

    public void clientCommunication() throws IOException{
        while(true){
           this.selector.select();

           Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
           while (keys.hasNext()) {
                SelectionKey key = keys.next();
                try{
                    if(key.isAcceptable()){
                        SocketChannel clientChannel = serverChannel.accept();
                        clientChannel.configureBlocking(false);

                        SelectionKey clientKey = clientChannel.register(this.selector, SelectionKey.OP_READ);
                        Client client = new Client(clientKey, 1024);
                        clientKey.attach(client);
                        System.out.println("Client Connected" + client);
                    }

                    if(key.isReadable()){
                        Client client = (Client) key.attachment();
                        String input = client.read();
                        if(input == null){
                            key.cancel();
                            continue;
                        }

                        client.parseCommands(input);
                    }
                    keys.remove();
                } catch(IOException e){
                    key.channel().close();
                    key.cancel();
                    System.out.println("Client error, connection closed");
                }
           }

        }
    }
}