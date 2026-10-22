package command;

import java.util.List;

import protocol.resp;
import store.store;

public class lrangeCommand  implements  command{
    @Override 
    public byte [] execute(List<String>args){
        if(args.size()<3){
            return resp.error("ERR wrong number of arguments for 'lrange' command");
        }
        String key=args.get(0);
        int start;
        int end;
        try {
            start=Integer.parseInt(args.get(1));
            end=Integer.parseInt(args.get(2));
        } catch (Exception e) {
            return resp.error("ERR value is not an integer or out of range");
        }
        List<String> result=store.lrange(key, start, end);
        return resp.array(result);
    }
}
