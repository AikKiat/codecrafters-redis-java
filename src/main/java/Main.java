

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;



public class Main {
  public static void main(String[] args) throws IOException{
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");
    EventLoop eventloop = new EventLoop(6379);
    eventloop.clientCommunication();
  }
}
