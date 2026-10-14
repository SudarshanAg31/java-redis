package store;
import java.util.concurrent.ConcurrentHashMap;
public class store {
    private static final ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();
    public static void set(String key, String value) {
        data.put(key, value);
    }
    public static String get(String key) {
        return data.get(key);   // null agar nahi mili
    }
}