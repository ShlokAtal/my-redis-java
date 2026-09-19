import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Main 
{

    public static void main(String[] args) throws IOException 
    {
        Map<String, String> store = new HashMap<>();

        ServerSocket serverSocket = new ServerSocket(6379);
        System.out.println("Redis server started on port 6379");

        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected");

        InputStream input = clientSocket.getInputStream();
        OutputStream output = clientSocket.getOutputStream();

        byte[] buffer = new byte[1024];

        while (true) 
        {
            int bytesRead = input.read(buffer);

            if (bytesRead == -1) 
            {
                break;
            }

            String request = new String(buffer, 0, bytesRead);
            String[] parts = request.split("\r\n");

            int argumentCount = Integer.parseInt(parts[0].substring(1));
            int argumentLength = Integer.parseInt(parts[1].substring(1));

            String command = parts[2];

            String message = null;

            if (argumentCount == 3) 
            {
                int messageLength = Integer.parseInt(parts[3].substring(1));
                message = parts[4];

                String key = parts[4];
                String value = parts[6];

                store.put(key, value);

                System.out.println("Message length: " + messageLength);
                System.out.println("Message: " + message);
            }

            System.out.println("Arguments: " + argumentCount);
            System.out.println("Command length: " + argumentLength);
            System.out.println("Command: " + command);

            if (command.length() == argumentLength && command.equals("PING")) 
            {
                output.write("+PONG\r\n".getBytes());
            } 
            else if (command.equals("SET") && message != null) 
            {
                output.write("+OK\r\n".getBytes());
            } 
            else if (command.equals("GET")) 
            {
                String key = parts[4];
                String value = store.get(key);

                output.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
            }
            else if(command.equals("DEL"))
            {
                String key=parts[4];
                int removed=store.remove(key)!=null ? 1:0;
                output.write((":"+removed+"\r\n").getBytes());
            }
            else if(command.equals("INCR"))
            {
                String key=parts[4];
                int value=Integer.parseInt(store.get(key))+1;
                store.put(key,String.valueOf(value));
                output.write((":"+value+"\r\n").getBytes());
            }
            else if(command.equals("DECR"))
            {
                String key = parts[4];
                int value = Integer.parseInt(store.get(key)) - 1;
                store.put(key, String.valueOf(value));
                output.write((":" + value + "\r\n").getBytes());
            } 
            else if (command.length() == argumentLength && command.equals("ECHO") && message != null) 
            {
                output.write(("$" + message.length() + "\r\n" + message + "\r\n").getBytes());
            } 
            else 
            {
                output.write("-ERR unknown command\r\n".getBytes());
            }
            output.flush();
        }
    }
}