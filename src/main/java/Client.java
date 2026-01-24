
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.io.IOException;
import java.util.ArrayDeque;


public class Client{
    private SocketChannel client;
    private ByteBuffer buffer;
    private Queue<String> commandsQueue;
    private Queue<String> responsesQueue;
    private boolean isMultiCalled;
    private boolean isExecCalled;
    private Datastore databaseSingleton;

    public Client(SelectionKey key, Integer bufferSize){
        client = (SocketChannel) key.channel();
        buffer = ByteBuffer.allocate(bufferSize);
        commandsQueue = new Queue<String>();
        responsesQueue = new Queue<String>();
        isMultiCalled = false;
        isExecCalled = false;
        databaseSingleton = Datastore.getInstance();
    }

    public void write(String toWrite) throws IOException{
        client.write(buffer.wrap(toWrite.getBytes()));  
    }

    public void setIsMultiCalled(boolean value){
        isMultiCalled = value;
    }

    public boolean getIsMultiCalled(){
        return isMultiCalled;
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
        // System.out.println("Received from Client " + client + ": " + input);

        if(isMultiCalled == true && !input.contains("EXEC")){
            storeCommandInQueue(input);
        }

        return input;
    }

    public void storeCommandInQueue(String command){
        commandsQueue.add(command);
    }

    public void storeResponseInQueue(String response){
        responsesQueue.add(response);
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
                        
                        if(isMultiCalled == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        if(isExecCalled == true){
                            responsesQueue.add(String.format("$%d\r\n%s\r\n", inputToEcho.length(), inputToEcho));
                            break;
                        }

                        write(String.format("$%d\r\n%s\r\n", inputToEcho.length(), inputToEcho));  
                        break;

                    case "PING":
                        if(isMultiCalled == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        if(isExecCalled == true){
                            responsesQueue.add("+PONG\r\n");
                            break;
                        }

                        write("+PONG\r\n");
                        break;
                        
                    case "SET":
                        if(isMultiCalled == true){
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

                        
                        if(isExecCalled == true){
                            responsesQueue.add("+OK\r\n");
                            break;
                        }

                        write("+OK\r\n");
                        break;
                    
                    case "GET":
                        System.out.println(""+isMultiCalled);
                        if(isMultiCalled == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        String rediskey = respInputParts[4];
                        Object redisValue = databaseSingleton.getKeyValue(rediskey);
                        if (redisValue != null){
                            String stringValue = String.valueOf(redisValue);

                            if (isExecCalled == true){
                                responsesQueue.add(String.format("$%d\r\n%s\r\n", stringValue.length(), stringValue));
                                break;
                            }


                            write(String.format("$%d\r\n%s\r\n", stringValue.length(), stringValue));
                        }
                        else{

                            if (isExecCalled == true){
                                responsesQueue.add("$-1\r\n");
                                break;
                            }

                            write("$-1\r\n");
                        }
                        break;
                    
                    case "INCR":
                        if(isMultiCalled == true){
                            write("+QUEUED\r\n");
                            break;
                        }

                        String incrKey = respInputParts[4];
                        Integer increResult = databaseSingleton.incrementKey(incrKey);
                        if(increResult != -1){

                            if(isExecCalled == true){
                                responsesQueue.add(String.format(":%d\r\n", increResult));
                                break;
                            }

                            write(String.format(":%d\r\n", increResult));
                        }
                        else{

                            if(isExecCalled == true){
                                responsesQueue.add("-ERR value is not an integer or out of range\r\n");
                                break;
                            }

                            write("-ERR value is not an integer or out of range\r\n");
                        }
                        break;
                    
                    case "MULTI":
                        isMultiCalled = true;
                        write("+OK\r\n");
                        break;

                    case "EXEC":
                
                        if(isMultiCalled == false){
                            //Means MULTI command was not called beforehand
                            write("-ERR EXEC without MULTI\r\n");
                            break;
                        }
                        if(commandsQueue.getSize() == 0){
                            write("*0\r\n");
                            isMultiCalled = false;
                            break;
                        }

                        System.out.println(commandsQueue.peek());
                        if(isMultiCalled == true){
                            isMultiCalled = false;
                        }

                        isExecCalled = true;
                        System.out.println("ismultiCalled now" + isMultiCalled);
                        System.out.println("Commands Queued" + commandsQueue.getQueue());
                        while (commandsQueue.getSize() > 0){
                            parseCommands(commandsQueue.popLatest());
                        }
                        writeRespArray(responsesQueue.getQueue());
                        isExecCalled = false;
                        break;
                }
            }
        } catch(IOException e){
            System.out.println("IO Exception occured");
        }

    }


    public void writeRespArray(ArrayDeque<String> responses) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("*").append(responses.size()).append("\r\n");

        for (String resp : responses) {
            sb.append(resp);
        }

        write(sb.toString());
    }
}
