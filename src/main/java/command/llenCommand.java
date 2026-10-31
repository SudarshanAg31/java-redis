package command;

import java.util.List;

import protocol.resp;
import store.store;

public class llenCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if(args.size()!=1){
            return resp.error("ERR worng number of arguments for 'llen' command");
        }
        String key=args.get(0);
        int length=store.llen(key);
        return resp.integer(length);
    }
}
