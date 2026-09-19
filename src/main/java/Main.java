import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class Main
{
    public static void main(String[] args) throws IOException
    {
        Map<String, String> store = new HashMap<>();
        Map<String, Long> expiry = new HashMap<>();
        Map<String, List<String>> lists = new HashMap<>();
        Map<String, Set<String>> sets = new HashMap<>();

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

            System.out.println("Arguments: " + argumentCount);
            System.out.println("Command length: " + argumentLength);
            System.out.println("Command: " + command);

            if (command.length() == argumentLength && command.equals("PING"))
            {
                output.write("+PONG\r\n".getBytes());
            }
            else if (command.equals("SET"))
            {
                String key = parts[4];
                String value = parts[6];

                store.put(key, value);

                output.write("+OK\r\n".getBytes());
            }
            else if (command.equals("GET"))
            {
                String key = parts[4];

                if (expiry.containsKey(key) && System.currentTimeMillis() >= expiry.get(key))
                {
                    store.remove(key);
                    expiry.remove(key);
                }

                String value = store.get(key);

                if (value == null)
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    output.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                }
            }
            else if (command.equals("DEL"))
            {
                String key = parts[4];

                int removed = store.remove(key) != null ? 1 : 0;

                output.write((":" + removed + "\r\n").getBytes());
            }
            else if (command.equals("INCR"))
            {
                String key = parts[4];

                int value = Integer.parseInt(store.get(key)) + 1;

                store.put(key, String.valueOf(value));

                output.write((":" + value + "\r\n").getBytes());
            }
            else if (command.equals("DECR"))
            {
                String key = parts[4];

                int value = Integer.parseInt(store.get(key)) - 1;

                store.put(key, String.valueOf(value));

                output.write((":" + value + "\r\n").getBytes());
            }
            else if (command.equals("EXISTS"))
            {
                String key = parts[4];

                int exists = store.containsKey(key) ? 1 : 0;

                output.write((":" + exists + "\r\n").getBytes());
            }
            else if (command.equals("INCRBY"))
            {
                String key = parts[4];
                int amount = Integer.parseInt(parts[6]);

                int value = Integer.parseInt(store.get(key)) + amount;

                store.put(key, String.valueOf(value));

                output.write((":" + value + "\r\n").getBytes());
            }
            else if(command.equals("DECRBY"))
            {
                String key = parts[4];
                int amount = Integer.parseInt(parts[6]);

                int value = Integer.parseInt(store.get(key)) - amount;
                store.put(key, String.valueOf(value));
                output.write((":" + value + "\r\n").getBytes());
            }
            else if (command.equals("SETNX"))
            {
                String key = parts[4];
                String value = parts[6];

                if (store.containsKey(key))
                {
                    output.write(":0\r\n".getBytes());
                }
                else
                {
                    store.put(key, value);
                    output.write(":1\r\n".getBytes());
                }
            }
            else if (command.equals("MGET"))
            {
                String key1 = parts[4];
                String key2 = parts[6];

                String value1 = store.get(key1);
                String value2 = store.get(key2);

                String response =
                                    "*2\r\n" +
                                    "$" + value1.length() + "\r\n" + value1 + "\r\n" +
                                    "$" + value2.length() + "\r\n" + value2 + "\r\n";
                output.write(response.getBytes());
            }
            else if (command.equals("MSET"))
            {
                String key1 = parts[4];
                String value1 = parts[6];

                String key2 = parts[8];
                String value2 = parts[10];

                store.put(key1, value1);
                store.put(key2, value2);
                output.write("+OK\r\n".getBytes());
            }
            else if (command.equals("GETSET"))
            {
                String key = parts[4];
                String newValue = parts[6];

                String oldValue = store.get(key);
                store.put(key, newValue);
                output.write(
                        ("$" + oldValue.length() + "\r\n" + oldValue + "\r\n").getBytes()
                    );
            }
            else if (command.equals("APPEND"))
            {
                String key = parts[4];
                String value = parts[6];
                String oldValue = store.get(key);

                if (oldValue == null)
                {
                    oldValue = "";
                }

                String newValue = oldValue + value;
                store.put(key, newValue);
                output.write((":" + newValue.length() + "\r\n").getBytes());
            }
            else if (command.equals("EXPIRE"))
            {
                String key = parts[4];
                int seconds = Integer.parseInt(parts[6]);

                if (store.containsKey(key))
                {
                    long expiryTime = System.currentTimeMillis() + (seconds * 1000L);
                    expiry.put(key, expiryTime);

                    output.write(":1\r\n".getBytes());
                }
                else
                {
                    output.write(":0\r\n".getBytes());
                }
            }
            else if(command.equals("TTL"))
            {
                String key=parts[4];
                if(!store.containsKey(key))
                {
                    output.write(":-2\r\n".getBytes());
                }
                else if(!expiry.containsKey(key))
                {
                    output.write(":-1\r\n".getBytes());
                }
                else
                {
                    long remaining=(expiry.get(key)-System.currentTimeMillis())/1000;
                    if(remaining<0)
                    {
                        store.remove(key);
                        expiry.remove(key);
                        output.write(":-2\r\n".getBytes());
                    }
                    else
                    {
                        output.write((":"+remaining+"\r\n").getBytes());
                    }
                }
            }
            else if(command.equals("PERSIST"))
            {
                String key=parts[4];
                if(expiry.remove(key)!=null)
                {
                    output.write(":1\r\n".getBytes());
                }
                else
                {
                    output.write(":0\r\n".getBytes());
                }
            }
            else if(command.equals("PTTL"))
            {
                String key=parts[4];
                if(!store.containsKey(key))
                {
                    output.write(":-2\r\n".getBytes());
                }
                else if(!expiry.containsKey(key))
                {
                    output.write(":-1\r\n".getBytes());
                }
                else
                {
                    long remaining = expiry.get(key) - System.currentTimeMillis();
                    if(remaining <= 0)
                    {
                        store.remove(key);
                        expiry.remove(key);
                        output.write(":-2\r\n".getBytes());
                    }
                    else
                    {
                        output.write((":" + remaining + "\r\n").getBytes());
                    }
                }
            }
            else if(command.equals("LPUSH"))
            {
                String key = parts[4];
                String value = parts[6];
                List<String> list = lists.get(key);

                if(list == null)
                {
                    list = new ArrayList<>();
                    lists.put(key, list);
                }

                list.add(0, value);
                output.write((":" + list.size() + "\r\n").getBytes());
            }
            else if(command.equals("RPUSH"))
            {
                String key = parts[4];
                String value = parts[6];
                List<String> list = lists.get(key);

                if(list == null)
                {
                    list = new ArrayList<>();
                    lists.put(key, list);
                }
                list.add(value);
                output.write((":" + list.size() + "\r\n").getBytes());
            }
            else if(command.equals("LPOP"))
            {
                String key = parts[4];
                List<String> list = lists.get(key);

                if(list == null || list.isEmpty())
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    String value = list.remove(0);
                    output.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                }
            }
            else if(command.equals("RPOP"))
            {
                String key = parts[4];

                List<String> list = lists.get(key);

                if(list == null || list.isEmpty())
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    String value = list.remove(list.size() - 1);
                    output.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                }
            }
            else if(command.equals("LRANGE"))
            {
                String key = parts[4];

                int start = Integer.parseInt(parts[6]);
                int stop = Integer.parseInt(parts[8]);
                List<String> list = lists.get(key);

                if(list == null)
                {
                    output.write("*0\r\n".getBytes());
                }
                else
                {
                    if(start < 0)
                    {
                        start = list.size() + start;
                    }
                    if(stop < 0)
                    {
                        stop = list.size() + stop;
                    }
                    if(start < 0)
                    {
                        start = 0;
                    }
                    if(stop >= list.size())
                    {
                        stop = list.size() - 1;
                    }
                    if(start > stop || start >= list.size())
                    {
                        output.write("*0\r\n".getBytes());
                    }
                    else
                    {
                        StringBuilder response = new StringBuilder();
                        int count = stop - start + 1;
                        response.append("*").append(count).append("\r\n");

                        for(int i = start; i <= stop; i++)
                        {
                            String value = list.get(i);
                            response.append("$")
                                .append(value.length())
                                .append("\r\n")
                                .append(value)
                                .append("\r\n");
                        }
                        output.write(response.toString().getBytes());
                    }
                }
            }
            else if(command.equals("LLEN"))
            {
                String key = parts[4];
                List<String> list = lists.get(key);

                if (list == null)
                {
                    output.write(":0\r\n".getBytes());
                }
                else
                {
                    output.write((":" + list.size() + "\r\n").getBytes());
                }
            }
            else if(command.equals("LINDEX"))
            {
                String key = parts[4];
                int index = Integer.parseInt(parts[6]);
                List<String> list = lists.get(key);

                if(list == null || index >= list.size() || index < -list.size())
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    if(index < 0)
                    {
                        index = list.size() + index;
                    }
                    String value = list.get(index);

                    output.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                }
            }
            else if(command.equals("LSET"))
            {
                String key = parts[4];
                int index = Integer.parseInt(parts[6]);
                String value = parts[8];
                List<String> list = lists.get(key);

                if(list == null || index >= list.size() || index < -list.size())
                {
                    output.write("-ERR index out of range\r\n".getBytes());
                }
                else
                {
                    if(index < 0)
                    {
                        index = list.size() + index;
                    }
                    list.set(index, value);
                    output.write("+OK\r\n".getBytes());
                }
            }
            else if(command.equals("LTRIM"))
            {
                String key = parts[4];
                int start = Integer.parseInt(parts[6]);
                int stop = Integer.parseInt(parts[8]);
                List<String> list = lists.get(key);

                if(list == null)
                {
                    output.write("+OK\r\n".getBytes());
                }
                else
                {
                    if(start < 0)
                    {
                        start = list.size() + start;
                    }
                    if(stop < 0)
                    {
                        stop = list.size() + stop;
                    }
                    if(start < 0)
                    {
                        start = 0;
                    }
                    if(stop >= list.size())
                    {
                        stop = list.size() - 1;
                    }
                    if(start > stop || start >= list.size())
                    {
                        list.clear();
                    }
                    else
                    {
                        List<String> trimmed = new ArrayList<>(list.subList(start, stop + 1));
                        list.clear();
                        list.addAll(trimmed);
                    }
                    output.write("+OK\r\n".getBytes());
                }
            }
            else if(command.equals("LPOS"))
            {
                String key = parts[4];
                String value = parts[6];
                List<String> list = lists.get(key);

                if(list == null)
                {
                    output.write(":-1\r\n".getBytes());
                }
                else
                {
                    int index = list.indexOf(value);
                    output.write((":" + index + "\r\n").getBytes());
                }
            }
            else if(command.equals("SADD"))
            {
                String key = parts[4];
                String member = parts[6];
                Set<String> set = sets.get(key);

                if(set == null)
                {
                    set = new HashSet<>();
                    sets.put(key, set);
                }
                int added = set.add(member) ? 1 : 0;
                output.write((":" + added + "\r\n").getBytes());
            }
            else if(command.equals("SREM"))
            {
                String key = parts[4];
                String member = parts[6];
                Set<String> set = sets.get(key);

                int removed = 0;
                if(set != null && set.remove(member))
                {
                    removed = 1;
                }
                output.write((":" + removed + "\r\n").getBytes());
            }
            else if(command.equals("SISMEMBER"))
            {
                String key = parts[4];
                String member = parts[6];
                Set<String> set = sets.get(key);

                int exists = (set != null && set.contains(member)) ? 1 : 0;
                output.write((":" + exists + "\r\n").getBytes());
            }
            else if(command.equals("SMEMBERS"))
            {
                String key = parts[4];
                Set<String> set = sets.get(key);
                if(set == null)
                {
                    output.write("*0\r\n".getBytes());
                }
                else
                {
                    StringBuilder response = new StringBuilder();
                    response.append("*").append(set.size()).append("\r\n");
                    for (String member : set)
                    {
                        response.append("$")
                            .append(member.length())
                            .append("\r\n")
                            .append(member)
                            .append("\r\n");
                    }
                    output.write(response.toString().getBytes());
                }
            }
            else if(command.equals("SCARD"))
            {
                String key = parts[4];
                Set<String> set = sets.get(key);
                int size = (set == null) ? 0 : set.size();
                output.write((":" + size + "\r\n").getBytes());
            }
            
            else if(command.equals("ECHO"))
            {
                String message = parts[4];
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