package store;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import store.store.ListLock;
import store.store.StreamEntry;
import store.store.StreamId;
import store.store.Waiter;
import store.store.XaddError;
public class store {

    private static StreamId resolveId(String strid, Stream stream) {
        if(strid.startsWith("*")){
            return autoGenerateId(stream);
        }
        if(strid.endsWith("-*")){
            String timeStr=strid.substring(0,strid.length()-2);
            long time;
            try {
                time=Long.parseLong(timeStr);
            }
            catch (NumberFormatException e) {
            throw new XaddError("ERR Invalid stream ID specified as stream command argument");
            }
            long seq;
            if(stream.entries.isEmpty()){
                seq=(time==0)?1:0;
            } 
            else{
                StreamId last=stream.entries.get(stream.entries.size()-1).id;
                if(time<last.time){
                    // Can't go backwards
                    throw new XaddError("ERR The ID specified in XADD is equal or smaller than the target stream top item");
                }
                else if(time==last.time){
                    // Same time — increment seq
                    seq=last.seq+1;
                }
                else{
                    // New time > last time
                    seq=(time==0)?1:0;
                }
            }
            return new StreamId(time, seq);
        }
        // Explicit Id
        try {
            return StreamId.parse(strid);
        } 
        
        catch (IllegalArgumentException e) {
            throw new XaddError("ERR Invalid stream ID specified as stream command argument");
        }
    }

    private static StreamId autoGenerateId(Stream stream) {
        long now=System.currentTimeMillis();
        if(stream.entries.isEmpty()){
            return new StreamId(now, 0);
        }
        StreamId last=stream.entries.get(stream.entries.size()-1).id;
        if(now>last.time){
            return new StreamId(now, 0);
        }
        if(now== last.time){
            return new StreamId(now, last.seq+1);
        }
        // now < last.time (clock skew)
        // use last.time, increment seq
        if(last.seq==Long.MAX_VALUE){
            return new StreamId(last.time+1, 0);
        }
        return new StreamId(last.time, last.seq+1);
    }
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
    
    static class Waiter {
        final long deadline;
        Waiter(long deadline) { this.deadline = deadline; }
    }
    
    public static class ListLock {
        final ReentrantLock lock = new ReentrantLock();//list me ek he time koi add ya delete ker sakta hai bake block 
        final Condition condition = lock.newCondition();//jo block hai un ko a weak ker ta hai aur signal deta hai
       //  await()  → Thread ko wait karata hai 😴
        // signal() → Waiting thread ko wake karta hai ⚡
        final Deque<Waiter> waiters = new ArrayDeque<>();//FIFO queue — kaun kaun wait kar raha hai, order me
    }
    
    public static class StreamEntry{
        public final StreamId id ;
        public final List<String> fields;
        public StreamEntry(StreamId id,List<String>fields){
            this.id=id;
            this.fields=fields;
        }
    }

    public static class Stream{
        public final List<StreamEntry> entries = new ArrayList<>();
    }
    
    public static class StreamId implements Comparable<StreamId>{
        public final long time;
        public final long seq;
        public StreamId(long time,long seq){
            this.time=time;
            this.seq=seq;
        }

        public static StreamId parse(String s) {
            int dash=s.indexOf('-');
            if(dash==-1){
                throw new IllegalArgumentException("Invalid ID format: " +s);
            }
            long time=Long.parseLong(s.substring(0,dash));
            long seq=Long.parseLong(s.substring(dash+1));
            return new StreamId(time,seq);
        }
        @Override
        public int compareTo(StreamId o) {
            int c=Long.compare(this.time,o.time);
            if(c!=0)return c;
            return Long.compare(this.seq, o.seq);
        }
        @Override 
        public String toString(){
            return time+"-"+seq;
        }
    }
    
    public static class XaddError extends RuntimeException {
    public XaddError(String message) { super(message); }
    }
    // store.java — BLPOP support
    //ConcurrentHashMap: is thread safe 
    private static final ConcurrentHashMap<String ,Stream>streams=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ListLock> blpopLocks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Entry> data = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> lists = new ConcurrentHashMap<>();
    private static ListLock getBlpopLock(String key) {
            return blpopLocks.computeIfAbsent(key, k -> new ListLock());
        }
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
    
    public static int rpush(String key, List<String> values) {
        ListLock ll = getBlpopLock(key);
        ll.lock.lock();
        try {
            List<String> list = lists.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
            list.addAll(values);
            if (!ll.waiters.isEmpty()) {
                ll.condition.signalAll();
            }
            return list.size();
        } finally {
            ll.lock.unlock();
        }
    }

    public static int lpush(String key, List<String> values) {
        ListLock ll = getBlpopLock(key);
        ll.lock.lock();
        try {
            List<String> list = lists.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
            List<String> reversed = new ArrayList<>(values);
            Collections.reverse(reversed);
            list.addAll(0, reversed);
            if (!ll.waiters.isEmpty()) {
                ll.condition.signalAll();
            }
            return list.size();
        } finally {
            ll.lock.unlock();
        }
    }
    
    public static List<String> lrange(String key,int start,int stop){
        ListLock ll = getBlpopLock(key);
        ll.lock.lock();
        try {
            List<String> list=lists.get(key);
            if(list==null)return new ArrayList<>(); //empty 
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
            if(start>=len) return new ArrayList<>();
            if(start>stop)return new ArrayList<>();
            if(stop>=len) stop=len-1;
            return new ArrayList<>(list.subList(start, stop+1));
        } finally{ 
            ll.lock.unlock();
        }
    }

    public static String[] blpop(String key, double timeoutSec) {
        ListLock ll = getBlpopLock(key);
        ll.lock.lock();
        try {
            long deadline = timeoutSec <= 0 ? 0 : System.currentTimeMillis() + (long)timeoutSec * 1000L;
            Waiter me = new Waiter(deadline);
            ll.waiters.addLast(me);

            while (true) {
                List<String> list = lists.get(key);
                if (list != null && !list.isEmpty() && ll.waiters.peekFirst() == me) {
                    ll.waiters.pollFirst();
                    String val = list.remove(0);
                    if (!list.isEmpty() && !ll.waiters.isEmpty()) {
                        ll.condition.signalAll();
                    }
                    return new String[]{ key, val };
                }

                if (timeoutSec > 0) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        ll.waiters.remove(me);
                        if (!ll.waiters.isEmpty()) ll.condition.signalAll();
                        return null;
                    }
                    ll.condition.await(remaining, TimeUnit.MILLISECONDS);
                } else {
                    ll.condition.await();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            ll.lock.unlock();
        }
    }

    public static int llen(String key){
        ListLock ll = getBlpopLock(key);
        ll.lock.lock();
        try {
            List<String> list=lists.get(key);
            if(list==null){
                return 0;
            }
            return list.size();
        } 
        finally {
            ll.lock.unlock();
        }
    }
    
    public static String lpop(String key) {
        ListLock ll = getBlpopLock(key);
        ll.lock.lock();
        try {
            List<String> list = lists.get(key);
            if (list == null || list.isEmpty()) {
                return null;
            }
            return list.remove(0);
        } finally {
            ll.lock.unlock();
        }
    }
    
    public static List<String> lpop(String key, int count) {
    ListLock ll = getBlpopLock(key);
    ll.lock.lock();
    try {
        List<String> list = lists.get(key);
        if (list == null) {
            return new ArrayList<>();
        }
        int n = Math.min(list.size(), count);
        if (n == 0) {
            return new ArrayList<>();
        }
        List<String> removed = new ArrayList<>(list.subList(0, n));
        list.subList(0, n).clear();
        // Koi waiter ko signal do (agar aur elements bache)
        if (!list.isEmpty() && !ll.waiters.isEmpty()) {
            ll.condition.signalAll();
        }
        return removed;
    } finally {
        ll.lock.unlock();
    }
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

    public static String type(String key){
        if(data.containsKey(key)){
            Entry e=data.get(key);
            if(e==null){
            // race — ignore, treat as missing
            }
            else if(e.isExpired()){
                data.remove(key);
            }
            else return "string";
        }
        if(lists.containsKey(key)){
            return "list";
        }
        else if(streams.contains(key)){
            return "stream";
        }
        return "none";
    } 

    public static String xadd(String key,String strid,List<String> fields){
        ListLock ll=getBlpopLock(key);
        ll.lock.lock();
        try {
            Stream stream=streams.computeIfAbsent(key,k->new Stream());
            StreamId newId=resolveId(strid,stream);
            // Rule 1: ID must be > 0-0
            if(newId.compareTo(new StreamId(0, 0))<=0){
                return "ERR The ID specified in XADD must be greater than 0-0";
            }
            // Rule 2: ID must be > last entry's ID (if any)
            if (!stream.entries.isEmpty()) {
                StreamId lastId = stream.entries.get(stream.entries.size() - 1).id;
                if (newId.compareTo(lastId) <= 0) {
                    return "ERR The ID specified in XADD is equal or smaller than the target stream top item";
                }
            }
            StreamEntry entry=new StreamEntry(newId,new ArrayList<>(fields));
            stream.entries.add(entry);
            return newId.toString();
        } finally {
            ll.lock.unlock();
        }
    }
    public static List<List<Object>> xrange(String key, StreamId start,StreamId end) {
        ListLock ll=getBlpopLock(key);
        ll.lock.lock();
        try {
            Stream stream=streams.get(key);
            if(stream==null){
                return new ArrayList<>();
            }
            List<List<Object>> result=new ArrayList<>();
            for(StreamEntry entry: stream.entries){
                if(entry.id.compareTo(start)>=0&& entry.id.compareTo(end)<=0){
                    //Entry in range
                    List<Object> item=new ArrayList<>();
                    item.add(entry.id.toString());
                    item.add(entry.fields);
                    result.add(item);
                }
            }
            return result;
        }
        finally{
            ll.lock.unlock();
        }
    }
    public static List<Object> xread(String key,StreamId startAfter){
        ListLock ll=getBlpopLock(key);
        ll.lock.lock();
        try {
            Stream stream=streams.get(key);
            if(stream==null){
                return List.of();
            }
            List<Object> entries = new ArrayList<>();
            for (StreamEntry entry : stream.entries) {
                // EXCLUSIVE: strictly greater
                if (entry.id.compareTo(startAfter) > 0) {
                    List<Object> item = new ArrayList<>();
                    item.add(entry.id.toString());
                    item.add(entry.fields);
                    entries.add(item);
                }
            }
            // If no entries, return empty (don't wrap with stream name)
            if (entries.isEmpty()) {
                return List.of();
            }
            // Wrap: [key, entries]
            List<Object> streamWrapper = new ArrayList<>();
            streamWrapper.add(key);
            streamWrapper.add(entries);
            
            // Outer: [streamWrapper]
            List<Object> result = new ArrayList<>();
            result.add(streamWrapper);
            return result;
        }
        finally{
            ll.lock.unlock();
        }
    }
}