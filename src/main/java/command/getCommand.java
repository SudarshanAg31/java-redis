package command;

import java.util.List;

import protocol.resp;
import store.store;

public class getCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if (args.size() != 1) {
            return resp.error("ERR wrong number of arguments for 'get' command");
        }
        String key = args.get(0);
        String value = store.get(key);
        if (value == null) {
            return resp.nullBulkString();
        }
        return resp.bulkString(value);
    }
}