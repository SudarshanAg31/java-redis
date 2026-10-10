package command;
import java.util.List;

import protocol.resp;
public class echoCommand implements command{
    @Override
    public byte [] execute(List<String>args){
        if(args.size()!=1){
            return resp.error("ERR wrong numberof argument for 'echo' command");
        }
        return resp.bulkString(args.get(0));
    }
}
