import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static void main(String[] args) throws IOException {

        ServerSocket serverSocket = new ServerSocket(6379);
        System.out.println("Redis server started on port 6379");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected");

        OutputStream output = clientSocket.getOutputStream();
        output.write("+PONG\r\n".getBytes());
        output.flush();
        clientSocket.getInputStream().read();
    }
}