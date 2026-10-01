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
import java.util.regex.Pattern;
import storage.RedisStore;

public class Main
{
    static boolean keyExists(
        String key,
        Map<String, String> store,
        Map<String, List<String>> lists,
        Map<String, Set<String>> sets,
        Map<String, Map<String, String>> hashes,
        Map<String, Map<String, Double>> sortedSets)
    {
            return store.containsKey(key)
            || lists.containsKey(key)
            || sets.containsKey(key)
            || hashes.containsKey(key)
            || sortedSets.containsKey(key);
    }

    static void removeKey(
        String key,
        Map<String, String> store,
        Map<String, Long> expiry,
        Map<String, List<String>> lists,
        Map<String, Set<String>> sets,
        Map<String, Map<String, String>> hashes,
        Map<String, Map<String, Double>> sortedSets)
    {
        store.remove(key);
        expiry.remove(key);
        lists.remove(key);
        sets.remove(key);
        hashes.remove(key);
        sortedSets.remove(key);
    }
    public static void main(String[] args) throws IOException
    {
        RedisStore redisStore = new RedisStore();

        Map<String, String> store = new HashMap<>();
        Map<String, Long> expiry = new HashMap<>();
        Map<String, List<String>> lists = new HashMap<>();
        Map<String, Set<String>> sets = new HashMap<>();
        Map<String, Map<String, String>> hashes = new HashMap<>();
        Map<String, Map<String, Double>> sortedSets = new HashMap<>();

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

            String[] commandArgs = new String[argumentCount - 1];

            for (int i = 0; i < commandArgs.length; i++)
            {
                commandArgs[i] = parts[4 + (i * 2)];
            }

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
                String key = commandArgs[0];
                String element = commandArgs[1];

                List<String> list = lists.get(key);

                if(list == null)
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    int rank = 1;
                    int count = 1;
                    int maxLen = list.size();

                    for(int i = 2; i < commandArgs.length; i++)
                    {
                        String option = commandArgs[i].toUpperCase();

                        if(option.equals("RANK") && i + 1 < commandArgs.length)
                        {
                            rank = Integer.parseInt(commandArgs[++i]);
                        }
                        else if(option.equals("COUNT") && i + 1 < commandArgs.length)
                        {
                            count = Integer.parseInt(commandArgs[++i]);
                        }
                        else if(option.equals("MAXLEN") && i + 1 < commandArgs.length)
                        {
                            maxLen = Integer.parseInt(commandArgs[++i]);
                        }
                    }

                    List<Integer> positions = new ArrayList<>();

                    if(rank >= 1)
                    {
                        int found = 0;

                        for(int i = 0; i < list.size() && i < maxLen; i++)
                        {
                            if(list.get(i).equals(element))
                            {
                                found++;

                                if(found >= rank)
                                {
                                    positions.add(i);

                                    if(positions.size() >= count)
                                    {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    else
                    {
                        int found = 0;
                        int minimum = Math.max(0, list.size() - maxLen);

                        for(int i = list.size() - 1; i >= minimum; i--)
                        {
                            if(list.get(i).equals(element))
                            {
                                found++;

                                if(found >= Math.abs(rank))
                                {
                                    positions.add(i);

                                    if(positions.size() >= count)
                                    {
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if(positions.isEmpty())
                    {
                        output.write("$-1\r\n".getBytes());
                    }
                    else if(count == 1)
                    {
                        output.write((":" + positions.get(0) + "\r\n").getBytes());
                    }
                    else
                    {
                        StringBuilder response = new StringBuilder();

                        response.append("*")
                                .append(positions.size())
                                .append("\r\n");

                        for(int position : positions)
                        {
                            response.append(":")
                                    .append(position)
                                    .append("\r\n");
                        }

                        output.write(response.toString().getBytes());
                    }
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
            else if(command.equals("HSET")) 
            {
                String key = parts[4];
                String field = parts[6];
                String value = parts[8];
                Map<String, String> hash = hashes.get(key);
                if(hash == null) 
                {
                    hash = new HashMap<>();
                    hashes.put(key, hash);
                }
                boolean exists = hash.containsKey(field);
                hash.put(field, value);
                output.write((":" + (exists ? 0 : 1) + "\r\n").getBytes());
            }
            else if(command.equals("HGET"))
            {
                String key=parts[4];
                String field=parts[6];
                Map<String, String> hash = hashes.get(key);
                if(hash == null || !hash.containsKey(field)) 
                {
                    output.write("$-1\r\n".getBytes());
                } 
                else 
                {
                    String value = hash.get(field);
                    output.write(("$" + value.length() + "\r\n" + value + "\r\n").getBytes());
                }
            }
            else if(command.equals("HDEL")) 
            {
                String key = parts[4];
                String field = parts[6];
                Map<String, String> hash = hashes.get(key);
                if(hash == null) 
                {
                    output.write(":0\r\n".getBytes());
                } 
                else 
                {
                    boolean removed = hash.remove(field) != null;
                    output.write((":" + (removed ? 1 : 0) + "\r\n").getBytes());
                }
            }
            else if(command.equals("HEXISTS")) 
            {
                String key = parts[4];
                String field = parts[6];
                Map<String, String> hash = hashes.get(key);
                boolean exists = hash != null && hash.containsKey(field);
                output.write((":" + (exists ? 1 : 0) + "\r\n").getBytes());
            }
            else if(command.equals("HGETALL")) 
            {
                String key = parts[4];
                Map<String, String> hash = hashes.get(key);
                if(hash == null)
                {
                    output.write("*0\r\n".getBytes());
                } 
                else 
                {
                    StringBuilder response = new StringBuilder();
                    response.append("*").append(hash.size() * 2).append("\r\n");
                    for (Map.Entry<String, String> entry : hash.entrySet()) 
                    {
                        String field = entry.getKey();
                        String value = entry.getValue();

                        response.append("$")
                                .append(field.length())
                                .append("\r\n")
                                .append(field)
                                .append("\r\n");

                        response.append("$")
                                .append(value.length())
                                .append("\r\n")
                                .append(value)
                                .append("\r\n");
                    }
                    output.write(response.toString().getBytes());
                }
            }
            else if(command.equals("ZADD")) 
            {
                String key = parts[4];
                double score = Double.parseDouble(parts[6]);
                String member = parts[8];
                Map<String, Double> sortedSet = sortedSets.computeIfAbsent(key, k -> new HashMap<>());

                boolean isNew = !sortedSet.containsKey(member);
                sortedSet.put(member, score);

                output.write((isNew ? ":1\r\n" : ":0\r\n").getBytes());
            }
            else if(command.equals("ZSCORE")) 
            {
                String key = parts[4];
                String member = parts[6];
                Map<String, Double> sortedSet = sortedSets.get(key);
                if(sortedSet == null || !sortedSet.containsKey(member)) 
                {
                    output.write("$-1\r\n".getBytes());
                } 
                else 
                {
                    String score = String.valueOf(sortedSet.get(member));
                    output.write(("$" + score.length() + "\r\n" + score + "\r\n").getBytes());
                }
            }
            else if (command.equals("ZREM")) 
            {
                String key = parts[4];
                String member = parts[6];
                Map<String, Double> sortedSet = sortedSets.get(key);
                if (sortedSet == null || !sortedSet.containsKey(member)) 
                {
                    output.write(":0\r\n".getBytes());
                } 
                else 
                {
                    sortedSet.remove(member);
                    if(sortedSet.isEmpty()) 
                    {
                        sortedSets.remove(key);
                    }
                    output.write(":1\r\n".getBytes());
                }
            }
            else if(command.equals("ZCARD")) 
            {
                String key = parts[4];
                Map<String, Double> sortedSet = sortedSets.get(key);
                int size = sortedSet == null ? 0 : sortedSet.size();
                output.write((":" + size + "\r\n").getBytes());
            }
            else if(command.equals("ZRANK")) 
            {
                String key = parts[4];
                String member = parts[6];
                Map<String, Double> sortedSet = sortedSets.get(key);
                if(sortedSet == null || !sortedSet.containsKey(member)) 
                {
                    output.write(":-1\r\n".getBytes());
                } 
                else 
                {
                    List<Map.Entry<String, Double>> entries =new ArrayList<>(sortedSet.entrySet());
                    entries.sort(Map.Entry.comparingByValue());
                    int rank = 0;
                    for(Map.Entry<String, Double> entry : entries) 
                    {
                        if(entry.getKey().equals(member)) 
                        {
                            break;
                        }
                        rank++;
                    }
                    output.write((":" + rank + "\r\n").getBytes());
                }
            }

            else if(command.equals("TYPE"))
            {
                String key = commandArgs[0];

                if(store.containsKey(key))
                {
                    output.write("+string\r\n".getBytes());
                }
                else if(lists.containsKey(key))
                {
                    output.write("+list\r\n".getBytes());
                }
                else if(sets.containsKey(key))
                {
                    output.write("+set\r\n".getBytes());
                }
                else if(hashes.containsKey(key))
                {
                    output.write("+hash\r\n".getBytes());
                }
                else if(sortedSets.containsKey(key))
                {
                    output.write("+zset\r\n".getBytes());
                }
                else
                {
                    output.write("+none\r\n".getBytes());
                }
            }
            else if(command.equals("RENAME"))
            {
                String oldKey = commandArgs[0];
                String newKey = commandArgs[1];

                if(!keyExists(oldKey, store, lists, sets, hashes, sortedSets))
                {
                    output.write("-ERR no such key\r\n".getBytes());
                }
                else
                {
                    if(store.containsKey(oldKey))
                    {
                        store.put(newKey, store.remove(oldKey));
                    }
                    if(lists.containsKey(oldKey))
                    {
                        lists.put(newKey, lists.remove(oldKey));
                    }
                    if(sets.containsKey(oldKey))
                    {
                        sets.put(newKey, sets.remove(oldKey));
                    }
                    if(hashes.containsKey(oldKey))
                    {
                        hashes.put(newKey, hashes.remove(oldKey));
                    }
                    if(sortedSets.containsKey(oldKey))
                    {
                        sortedSets.put(newKey, sortedSets.remove(oldKey));
                    }
                    if(expiry.containsKey(oldKey))
                    {
                        expiry.put(newKey, expiry.remove(oldKey));
                    }

                    output.write("+OK\r\n".getBytes());
                }
            }
            else if(command.equals("RENAMENX"))
            {
                String oldKey = commandArgs[0];
                String newKey = commandArgs[1];

                if(!keyExists(oldKey, store, lists, sets, hashes, sortedSets))
                {
                    output.write("-ERR no such key\r\n".getBytes());
                }
                else if(keyExists(newKey, store, lists, sets, hashes, sortedSets))
                {
                    output.write(":0\r\n".getBytes());
                }
                else
                {
                    if(store.containsKey(oldKey))
                    {
                        store.put(newKey, store.remove(oldKey));
                    }
                    if(lists.containsKey(oldKey))
                    {
                        lists.put(newKey, lists.remove(oldKey));
                    }
                    if(sets.containsKey(oldKey))
                    {
                        sets.put(newKey, sets.remove(oldKey));
                    }
                    if(hashes.containsKey(oldKey))
                    {
                        hashes.put(newKey, hashes.remove(oldKey));
                    }
                    if(sortedSets.containsKey(oldKey))
                    {
                        sortedSets.put(newKey, sortedSets.remove(oldKey));
                    }
                    if(expiry.containsKey(oldKey))
                    {
                        expiry.put(newKey, expiry.remove(oldKey));
                    }

                    output.write(":1\r\n".getBytes());
                }
            }
            else if(command.equals("KEYS"))
            {
                String pattern = commandArgs[0];

                String regex = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*")
                        .replace("?", ".");

                Pattern compiledPattern = Pattern.compile("^" + regex + "$");

                Set<String> allKeys = new HashSet<>();
                allKeys.addAll(store.keySet());
                allKeys.addAll(lists.keySet());
                allKeys.addAll(sets.keySet());
                allKeys.addAll(hashes.keySet());
                allKeys.addAll(sortedSets.keySet());

                List<String> matchingKeys = new ArrayList<>();

                for(String key : allKeys)
                {
                    if(compiledPattern.matcher(key).matches())
                    {
                        matchingKeys.add(key);
                    }
                }

                StringBuilder response = new StringBuilder();
                response.append("*").append(matchingKeys.size()).append("\r\n");

                for(String key : matchingKeys)
                {
                    response.append("$")
                            .append(key.length())
                            .append("\r\n")
                            .append(key)
                            .append("\r\n");
                }

                output.write(response.toString().getBytes());
            }
            else if(command.equals("DBSIZE"))
            {
                Set<String> allKeys = new HashSet<>();
                allKeys.addAll(store.keySet());
                allKeys.addAll(lists.keySet());
                allKeys.addAll(sets.keySet());
                allKeys.addAll(hashes.keySet());
                allKeys.addAll(sortedSets.keySet());

                output.write((":" + allKeys.size() + "\r\n").getBytes());
            }
            else if(command.equals("FLUSHDB"))
            {
                store.clear();
                expiry.clear();
                lists.clear();
                sets.clear();
                hashes.clear();
                sortedSets.clear();

                output.write("+OK\r\n".getBytes());
            }
            else if(command.equals("GETDEL"))
            {
                String key = commandArgs[0];
                String value = store.get(key);

                if(value == null)
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    store.remove(key);
                    expiry.remove(key);

                    output.write(("$" + value.length() + "\r\n" +
                            value + "\r\n").getBytes());
                }
            }
            else if(command.equals("GETEX"))
            {
                String key = commandArgs[0];
                String value = store.get(key);

                if(value == null)
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    if(commandArgs.length >= 2)
                    {
                        String option = commandArgs[1].toUpperCase();

                        if(option.equals("EX") && commandArgs.length >= 3)
                        {
                            long seconds = Long.parseLong(commandArgs[2]);
                            expiry.put(key, System.currentTimeMillis() + (seconds * 1000L));
                        }
                        else if(option.equals("PX") && commandArgs.length >= 3)
                        {
                            long milliseconds = Long.parseLong(commandArgs[2]);
                            expiry.put(key, System.currentTimeMillis() + milliseconds);
                        }
                        else if(option.equals("PERSIST"))
                        {
                            expiry.remove(key);
                        }
                    }

                    output.write(("$" + value.length() + "\r\n" +
                            value + "\r\n").getBytes());
                }
            }
            else if(command.equals("INCRBYFLOAT"))
            {
                String key = commandArgs[0];
                double increment = Double.parseDouble(commandArgs[1]);

                double current = 0;

                if(store.containsKey(key))
                {
                    current = Double.parseDouble(store.get(key));
                }

                double result = current + increment;
                store.put(key, String.valueOf(result));

                String value = String.valueOf(result);

                output.write(("$" + value.length() + "\r\n" +
                        value + "\r\n").getBytes());
            }
            else if(command.equals("STRLEN"))
            {
                String key = commandArgs[0];
                String value = store.get(key);

                int length = value == null ? 0 : value.length();

                output.write((":" + length + "\r\n").getBytes());
            }
            else if(command.equals("SETRANGE"))
            {
                String key = commandArgs[0];
                int offset = Integer.parseInt(commandArgs[1]);
                String replacement = commandArgs[2];

                String value = store.get(key);

                if(value == null)
                {
                    value = "";
                }

                StringBuilder result = new StringBuilder(value);

                while(result.length() < offset)
                {
                    result.append(" ");
                }

                for(int i = 0; i < replacement.length(); i++)
                {
                    int position = offset + i;

                    if(position < result.length())
                    {
                        result.setCharAt(position, replacement.charAt(i));
                    }
                    else
                    {
                        result.append(replacement.charAt(i));
                    }
                }

                store.put(key, result.toString());

                output.write((":" + result.length() + "\r\n").getBytes());
            }
            else if(command.equals("GETRANGE"))
            {
                String key = commandArgs[0];
                int start = Integer.parseInt(commandArgs[1]);
                int end = Integer.parseInt(commandArgs[2]);

                String value = store.get(key);

                if(value == null)
                {
                    value = "";
                }

                if(start < 0)
                {
                    start = value.length() + start;
                }

                if(end < 0)
                {
                    end = value.length() + end;
                }

                if(start < 0)
                {
                    start = 0;
                }

                if(end >= value.length())
                {
                    end = value.length() - 1;
                }

                if(start > end || start >= value.length())
                {
                    output.write("$0\r\n\r\n".getBytes());
                }
                else
                {
                    String result = value.substring(start, end + 1);

                    output.write(("$" + result.length() + "\r\n" +
                            result + "\r\n").getBytes());
                }
            }
            else if(command.equals("SPOP"))
            {
                String key = commandArgs[0];
                Set<String> set = sets.get(key);

                if(set == null || set.isEmpty())
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    String member = set.iterator().next();
                    set.remove(member);

                    if(set.isEmpty())
                    {
                        sets.remove(key);
                    }

                    output.write(("$" + member.length() + "\r\n" +
                            member + "\r\n").getBytes());
                }
            }
            else if(command.equals("SRANDMEMBER"))
            {
                String key = commandArgs[0];
                Set<String> set = sets.get(key);

                if(set == null || set.isEmpty())
                {
                    output.write("$-1\r\n".getBytes());
                }
                else
                {
                    String member = set.iterator().next();

                    output.write(("$" + member.length() + "\r\n" +
                            member + "\r\n").getBytes());
                }
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