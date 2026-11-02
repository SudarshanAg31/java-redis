package command;

import java.util.List;

import protocol.resp;
import store.store;

public class blpopCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        // BLPOP key timeout  (is stage me single key)
        if (args.size() != 2) {
            return resp.error("ERR wrong number of arguments for 'blpop' command");
        }

        String key = args.get(0);
        double timeout;
        try {
            timeout = Double.parseDouble(args.get(1));
        } catch (NumberFormatException e) {
            return resp.error("ERR timeout is not an integer or out of range");
        }
        if (timeout < 0) {
            return resp.error("ERR timeout is negative");
        }

        String[] result = store.blpop(key, timeout);

        if (result == null) {
            return resp.nullArray();   // *-1\r\n
        }
        return resp.array(List.of(result[0], result[1]));
    }
}