package command;

import java.util.List;

import protocol.resp;
import store.store;

public class xaddCommand implements command{
    @Override
    public byte[] execute(List<String> args) {
        if(args.size()<4){
            return resp.error("ERR wrong number of arguments for 'xadd' command");
        }
        String key=args.get(0);
        String id=args.get(1);
        List<String>fields=args.subList(2, args.size());
        if(fields.size()%2!=0){
            return resp.error("ERR wrong number of argument for 'xadd' command");
        }
        String resultId=store.xadd(key, id, fields);
        return resp.bulkString(resultId);
    }
}
