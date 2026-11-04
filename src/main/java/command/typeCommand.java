package command;

import java.util.List;

import protocol.resp;
import store.store;

public class typeCommand implements command{
    @Override
    public byte[] execute(List<String> args) {
        if(args.size()!=1){
            return resp.error("ERR wrong number of argument for 'type' command");
        }
        String key=args.get(0);
        String type=store.type(key);
        return resp.simpleString(type);
    }

}
