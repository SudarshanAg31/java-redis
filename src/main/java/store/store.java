package store;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
public class store {
    public static class Entry{
        public final String value;
        public final long expiryTime;
        public Entry(String value,long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
        public boolean isExpired(){
            return expiryTime>0 && System.currentTimeMillis()>expiryTime;
        }
    }
    //ConcurrentHashMap: is thread safe 
    private static final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> lists = new ConcurrentHashMap<>();
    //normal input with no expiry
    public static void set(String key, String value) {
        data.put(key, new Entry(value,0));
    }
    //set with expiry with millisecond from now
    public static void setWithExpiryMs(String key,String value,long ttlMs){
        long expiryTime=System.currentTimeMillis()+ttlMs;
        data.put(key,new Entry(value, expiryTime));
    }
    //set with expiry with second from now
    public static void setWithExpirySec(String key, String value, long ttlSec){
    long expiryTime = System.currentTimeMillis() + ttlSec * 1000L;
    data.put(key, new Entry(value, expiryTime));
    }
    public static int rpush(String key, List<String> value) {
    List<String> list = lists.computeIfAbsent(
        // Agar list pehle se map mein hai, toh usi existing list ko return karega.
        // Agar key nahi hai, toh nayi list create karke map mein store karega aur use return karega.
        key, k -> new CopyOnWriteArrayList<>()
    );
    list.addAll(value);
    return list.size();
    }
    public static List<String> lrange(String key,int start,int stop){
        List<String> list=lists.get(key);
        if(list==null)return List.of(); //empty 
        int len=list.size();
        // Normalize negative indexes
        if (start < 0) {
            start = len + start;
        }
        if (stop < 0) {
            stop = len + stop;
        }
        // Clamp start to 0 if still negative (out of range negative like -6 on len=5)
        if (start < 0) {
            start = 0;
        }
        if(start>=len) return List.of();
        if(start>stop)return List.of();
        if(stop>=len) stop=len-1;
        return new ArrayList<>(list.subList(start, stop+1));
    }
    public static String get(String key) {
        Entry entry=data.get(key);
        if(entry==null){
            return null;
        }
        if(entry.isExpired()){
            data.remove(key);
            return null;
        }
        return entry.value;  
    }
}