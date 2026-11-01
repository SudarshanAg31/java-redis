package command;

import java.util.List;

import protocol.resp;
import store.store;

public class lpopCommand implements command {

    @Override
    public byte[] execute(List<String> args) {
        if(args.size()<1||args.size()>2){
            return resp.error("ERR wrong of arguments for'lpop' command");
        }
        String key=args.get(0);
        if(args.size()==1){
            String value=store.lpop(key);
            if(value==null){
                return resp.nullBulkString();
            }
            return resp.bulkString(value);
        }
        int count;
        try {
            count=Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            return resp.error("ERR value is not an integer or out of range");
        }
        if(count<0){
            return resp.error("ERR value is out of range, must be positive");
        }
        List<String> removeStrings=store.lpop(key, count);
        return resp.array(removeStrings);
    }

}
