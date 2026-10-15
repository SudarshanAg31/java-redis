package store;
import java.util.concurrent.ConcurrentHashMap;
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