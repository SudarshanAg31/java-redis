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
import store.store.Waiter;
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
    // store.java — BLPOP support
    //ConcurrentHashMap: is thread safe 
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
        } finally{ 
            ll.lock.unlock();
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
            return List.of();
        }
        int n = Math.min(list.size(), count);
        if (n == 0) {
            return List.of();
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
}