import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import command.command;
import command.echoCommand;//server banane ke liye.
import command.pingCommand;
import protocol.respParser;

public class Main {
  public static void main(String[] args){
    System.out.println("Logs from your program will appear here!");
       try( ServerSocket serverSocket = new ServerSocket(6379) ){
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
    try (Socket s = client;
         InputStream in =s.getInputStream();
         OutputStream out =s.getOutputStream()) {
      while (true) {
        List<String> request = respParser.parse(in);
        if (request == null) {
          break;
        }
        String commandName = request.get(0);
        List<String> commandArgs = request.subList(1, request.size());
        command command;
        if (commandName.equalsIgnoreCase("PING")) {
          command = new pingCommand();
        } 
        else if (commandName.equalsIgnoreCase("ECHO")) {
          command = new echoCommand();
        } else {
          out.write(("-ERR unknown command\r\n").getBytes());
          out.flush();
          continue;
        }
        byte[] response = command.execute(commandArgs);
        out.write(response);
        out.flush();
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
} 