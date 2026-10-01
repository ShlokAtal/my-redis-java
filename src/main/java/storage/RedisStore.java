package storage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisStore 
{

    public final Map<String, String> store = new HashMap<>();
    public final Map<String, Long> expiry = new HashMap<>();
    public final Map<String, List<String>> lists = new HashMap<>();
    public final Map<String, Set<String>> sets = new HashMap<>();
    public final Map<String, Map<String, String>> hashes = new HashMap<>();
    public final Map<String, Map<String, Double>> sortedSets = new HashMap<>();
    
}