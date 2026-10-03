import java.io.IOException;//networking mein aane wali errors handle karne ke liye
import java.io.InputStream;
import java.net.ServerSocket;//server banane ke liye.
import java.net.Socket;//client ke saath connection ke liye.

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");

       ServerSocket serverSocket = null;//Ye server ka listening socket hai.
       Socket clientSocket = null;//Ye particular client ke saath established connection ko represent karega.
       int port = 6379;
       try {
         serverSocket = new ServerSocket(port);//"Operating system, mere liye port 6379 par ek TCP server socket create kar do."
         // Since the tester restarts your program quite often, setting SO_REUSEADDR
         // ensures that we don't run into 'Address already in use' errors
         serverSocket.setReuseAddress(true);//Ye mainly testing/restarting ke case mein useful hai.
         // Wait for connection from client.
         clientSocket = serverSocket.accept();//server ko client ka wait karwati hai.
         //mein client ke saath established connection aa jayega.

         // for single command
        //  clientSocket.getOutputStream().write("+PONG\r\n".getBytes());//Ye socket ka OutputStream deta hai.
         //Ye Redis ka response format hai.
        //+ → Redis Simple String response indicate karta hai.
        //\r\n → Carriage Return + Line Feed, yani Redis protocol ka line ending.

        //for multiple command 
        InputStream inputStream = clientSocket.getInputStream();
         byte[] buffer = new byte[1024];
         int bytesRead;
         while ((bytesRead = inputStream.read(buffer)) != -1) {
           clientSocket.getOutputStream().write("+PONG\r\n".getBytes());
         }
       }
        
       catch (IOException e) {
        System.out.println("IOException: " + e.getMessage());
       } finally {
         try {
           if (clientSocket != null) {
             clientSocket.close();
           }
         } catch (IOException e) {
           System.out.println("IOException: " + e.getMessage());
         }
       }
  }
}
