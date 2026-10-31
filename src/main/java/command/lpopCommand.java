package command;

import java.util.List;

import protocol.resp;
import store.store;

public class lpopCommand implements command {

    @Override
    public byte[] execute(List<String> args) {
        if(args.size()!=1){
            return resp.error("ERR wrong of arguments for'lpop' command");
        }
        String key=args.get(0);
        String removedString=store.lpop(key);
        if(removedString==null){
            return resp.nullBulkString();
        }
        return resp.bulkString(removedString);
    }
    
}
