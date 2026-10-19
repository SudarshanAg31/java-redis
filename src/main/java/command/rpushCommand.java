package command;
import java.util.List;

import protocol.resp;
import store.store;

public class rpushCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if (args.size() < 2) {
            return resp.error("ERR wrong number of arguments for 'rpush' command");
        }
        String key = args.get(0);
        String value = args.get(1);
        int size = store.rpush(key, value);
        return resp.integer(size);
    }
}
