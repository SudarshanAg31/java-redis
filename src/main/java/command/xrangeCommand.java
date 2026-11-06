package command;

import java.util.List;

import protocol.resp;
import store.store;

public class xrangeCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if(args.size()!=3){
            return resp.error("ERR wrong number of arguments for 'xrange' command");
        }
        String key=args.get(0);
        String startStr=args.get(1);
        String endStr=args.get(2);
    
        store.StreamId start;
        store.StreamId end;

        try {
            start = parseRangeId(startStr, true);
            end = parseRangeId(endStr, false);
        }
         catch (IllegalArgumentException e) {
            return resp.error("ERR Invalid stream ID specified as stream command argument");
        }
        List<List<Object>> result = store.xrange(key, start, end);
        return resp.encode(result);
    }
    /**
     * Parse ID with defaults.
     * isStart: true → seq default 0; false → seq default MAX
     */
    private store.StreamId parseRangeId(String s, boolean isStart) {
        // Special: "-" means minimum ID (0-0)
        if (s.equals("-")) {
            return new store.StreamId(0, 0);
        }
        if (s.equals("+")) {
        return new store.StreamId(Long.MAX_VALUE, Long.MAX_VALUE);
    }
        int dash = s.indexOf('-');
        if (dash == -1) {
            // Only time part
            long time = Long.parseLong(s);
            long seq = isStart ? 0 : Long.MAX_VALUE;
            return new store.StreamId(time, seq);
        }
        long time = Long.parseLong(s.substring(0, dash));
        long seq = Long.parseLong(s.substring(dash + 1));
        return new store.StreamId(time, seq);
    }
}
