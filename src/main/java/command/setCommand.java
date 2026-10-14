package command;

import java.util.List;

import protocol.resp;
import store.store;

public class setCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if (args.size() != 2) {
            return resp.error("ERR wrong number of arguments for 'set' command");
        }
        String key = args.get(0);
        String value = args.get(1);
        store.set(key, value);
        return resp.simpleString("OK");
    }
}