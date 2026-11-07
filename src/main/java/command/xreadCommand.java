package command;

import java.util.ArrayList;
import java.util.List;

import protocol.resp;
import store.store;

public class xreadCommand implements  command{
    
    @Override 
    public byte[] execute(List<String> args){
    // XREAD STREAMS key id
        // commandArgs = ["STREAMS", key, id]  → 3 args
        if (args.size() != 3) {
            return resp.error("ERR wrong number of arguments for 'xread' command");
        }

        if (!args.get(0).equalsIgnoreCase("STREAMS")) {
            return resp.error("ERR syntax error");
        }

        int remaining = args.size() - 1;   // exclude "STREAMS"
        if (remaining % 2 != 0) {
            return resp.error("ERR Unbalanced XREAD list of streams: for each stream key an ID or '$' must be specified.");
        }

        int n = remaining / 2;   // number of streams
        List<String> keys = args.subList(1, 1 + n);          // keys at index 1..n
        List<String> ids  = args.subList(1 + n, args.size()); // ids after keys

        // Parse IDs
        List<store.StreamId> startAfters = new ArrayList<>();
        for (String idStr : ids) {
            try {
                startAfters.add(parseId(idStr));
            } catch (IllegalArgumentException e) {
                return resp.error("ERR Invalid stream ID specified as stream command argument");
            }
        }

        List<Object> result = store.xreadMulti(keys, startAfters);
        
        if (result.isEmpty()) {
            return resp.nullArray();   // *-1\r\n
        }
        return resp.encode(result);
    }

    private store.StreamId parseId(String s) {
        // Special: "-" means minimum (0-0)
        if (s.equals("-")) {
            return new store.StreamId(0, 0);
        }
        if (s.equals("+")) {
            return new store.StreamId(Long.MAX_VALUE, Long.MAX_VALUE);
        }
        
        int dash = s.indexOf('-');
        if (dash == -1) {
            long time = Long.parseLong(s);
            return new store.StreamId(time, 0);   // seq default 0 (exclusive → next entries)
        }
        long time = Long.parseLong(s.substring(0, dash));
        long seq = Long.parseLong(s.substring(dash + 1));
        return new store.StreamId(time, seq);
    }
}
