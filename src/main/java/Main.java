import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;//server banane ke liye.
public class Main {
  public static void main(String[] args){
    System.out.println("Logs from your program will appear here!");
       ServerSocket serverSocket = null;
       int port = 6379;
       try {
         serverSocket = new ServerSocket(port);
         serverSocket.setReuseAddress(true);
         while (true) { 
          Socket clientSocket = serverSocket.accept();
          Runnable task = () -> handleClient(clientSocket);
          Thread thread = new Thread(task);
          thread.start();
         }
      }
       catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
       }
  }
  public static void handleClient(Socket client) {
    String responseMessage="+PONG";
    try (Socket s=client; 
        BufferedReader in =new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out=new PrintWriter(s.getOutputStream(),true);){
          String message;
          while((message=in.readLine())!=null){
            if(message.startsWith("*")){
              in.readLine();
              in.readLine();
              out.print(responseMessage+"\r\n");
              out.flush();
            }
            else{
              break;
            }
          }
        }
     catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}