package command;

import java.util.List;

import protocol.resp;
import store.store;

public class setCommand implements command {
    @Override
    public byte[] execute(List<String> args) {
        if (args.size() < 2) {
            return resp.error("ERR wrong number of arguments for 'set' command");
        }
        String key = args.get(0);
        String value = args.get(1);
        if(args.size()==2){
            store.set(key, value);
            return resp.simpleString("OK");
        }
        if(args.size()==4){
            String option=args.get(2).toUpperCase();
            long time;
            try {
                time=Long.parseLong(args.get(3));
            } 
            catch (NumberFormatException e) {
                return resp.error("ERR value is not an integer or out of range"); 
            }
            if(time<=0){
                return resp.error("ERR invalid expire time in 'set' command");
            }
            if(option.equals("PX")){
                store.setWithExpiryMs(key,value,time);
                return resp.simpleString("OK");
            }
            else if(option.equals("EX")){
                store.setWithExpirySec(key, value, time);
                return resp.simpleString("OK");
            }
            else{
                return resp.error("ERR syntax error");
            }
        }
        return resp.error("ERR syntax error");
    }
}