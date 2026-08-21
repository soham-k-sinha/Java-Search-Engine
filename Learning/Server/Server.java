package Learning.Server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

// Trying to make a really simple server that serves HTTP requests locally
public class Server {
    public static void main(String[] args) {
        int port = 8080;
        try (ServerSocket serverSocket = new ServerSocket(port)){

            while (true) {
                try (Socket socket = serverSocket.accept()){
                    BufferedReader inputStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter outputStream = new PrintWriter(socket.getOutputStream(), true);

                    String clientMessage = inputStream.readLine();
                    String[] req = clientMessage.split(" ");
                    if (req[1].equals("/")) {
                        System.out.println("Client said: " + clientMessage);
                        outputStream.println("HTTP/1.1 200 OK\r\n\r\n<html><body><h1>Welcome</h1></body></html>");
                    } else if (req[1].equals("/users")) {
                        System.out.println("Client said: " + clientMessage);
                        outputStream.println("HTTP/1.1 200 OK\r\n\r\n{{'name': 'sk', 'id': 1, 'age': 25}, {'name': 'lp', 'id': 2, 'age': 29}}");
                    } else {
                        System.out.println("Client said: " + clientMessage);
                        outputStream.println("HTTP/1.1 404 Not Found\r\n\r\n Page doesn't exist");
                    }
                } catch (IOException e) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }
}