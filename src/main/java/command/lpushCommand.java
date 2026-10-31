package command;

import java.util.List;

import protocol.resp;
import store.store;

public class lpushCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if(args.size()<2){
            return resp.error("ERR wrong number og arguments for 'lpush' command");
        }
        String key=args.get(0);
        List<String>values=args.subList(1, args.size());
        int size=store.lpush(key, values);
        return resp.integer(size);
    }
} 
