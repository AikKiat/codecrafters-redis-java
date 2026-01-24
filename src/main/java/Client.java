
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.io.IOException;


public class Client{
    private SocketChannel client;
    private ByteBuffer buffer;
    private Queue<String> commandsQueue;
    private boolean isMulti;
    private Datastore databaseSingleton;

    public Client(SelectionKey key, Integer bufferSize){
        client = (SocketChannel) key.channel();
        buffer = ByteBuffer.allocate(bufferSize);
        commandsQueue = new Queue<String>();
        isMulti = false;
        databaseSingleton = Datastore.getInstance();
    }

    public void write(String toWrite) throws IOException{
        client.write(buffer.wrap(toWrite.getBytes()));  
    }

    public void setIsMulti(boolean value){
        isMulti = value;
    }

    public boolean getIsMulti(){
        return isMulti;
    }

    public String read() throws IOException{
        int bytesRead = client.read(this.buffer);
        if (bytesRead == -1){
            client.close();
            System.out.println("Client Disconnected");
            return null;
        }
        buffer.flip(); // prepare for reading first

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        buffer.clear(); // clear the buffer, then we prepare for next read

        String input = new String(data);
        System.out.println("Received from Client " + client + ": " + input);

        if(isMulti == true && !input.contains("EXEC")){
            storeCommandInQueue(input);
        }

        return input;
    }

    public void storeCommandInQueue(String command){
        commandsQueue.add(command);
    }

    public String getLatestQueuedCommand(){
        if (commandsQueue.getSize() > 0){
            return commandsQueue.popLatest();
        }
        return null;
    }


    public void parseCommands(String input) throws IOException{
        try{

        
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
                        if(isMulti == true){
                            write("+QUEUED\r\n");
                            break;
                        }
                        write(String.format("$%d\r\n%s\r\n", inputToEcho.length(), inputToEcho));  
                        break;

                    case "PING":
                        if(isMulti == true){
                            write("+QUEUED\r\n");
                            break;
                        }
                        write("+PONG\r\n");
                        break;
                        
                    case "SET":
                        if(isMulti == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        String keyToSet = respInputParts[4];
                        String valueToSet = respInputParts[6];
                        Long expiryTime = -1L;
                        if(respInputParts.length >= 10 && respInputParts[8].equalsIgnoreCase("PX")){
                            System.out.println(respInputParts[8]);
                            System.out.println(respInputParts[7]);
                            expiryTime = Long.parseLong(respInputParts[10]);
                        }
                        databaseSingleton.setKeyValue(keyToSet, valueToSet, expiryTime);
                        write("+OK\r\n");
                        break;
                    
                    case "GET":
                        System.out.println(""+isMulti);
                        if(isMulti == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        String rediskey = respInputParts[4];
                        Object redisValue = databaseSingleton.getKeyValue(rediskey);
                        if (redisValue != null){
                            String stringValue = String.valueOf(redisValue);
                            write(String.format("$%d\r\n%s\r\n", stringValue.length(), stringValue));
                        }
                        else{
                            write("$-1\r\n");
                        }
                        break;
                    
                    case "INCR":
                        if(isMulti == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        String incrKey = respInputParts[4];
                        Integer increResult = databaseSingleton.incrementKey(incrKey);
                        if(increResult != -1){
                            write(String.format(":%d\r\n", increResult));
                        }
                        else{
                            write("-ERR value is not an integer or out of range\r\n");
                        }
                        break;
                    
                    case "MULTI":
                        isMulti = true;
                        write("+OK\r\n");
                        break;

                    case "EXEC":
                        System.out.println("ismulti during exec" + isMulti);
                        if(isMulti == false){
                            //Means MULTI command was not called beforehand
                            write("-ERR EXEC without MULTI\r\n");
                            break;
                        }
                        if(commandsQueue.getSize() == 0){
                            write("*0\r\n");
                            isMulti = false;
                            break;
                        }

                        System.out.println(commandsQueue.peek());
                        if(isMulti == true){
                            isMulti = false;
                        }
                        while (commandsQueue.getSize() > 0){
                            parseCommands(commandsQueue.popLatest());
                        }
                        break;
                }
            }
        } catch(IOException e){
            System.out.println("IO Exception occured");
        }

    }
}