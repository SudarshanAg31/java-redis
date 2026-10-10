package command;
import java.util.List;
import protocol.resp;
public class pingCommand implements command {
    @Override
    public byte [] execute(List<String>args){
        return resp.simpleString("PONG");
    }
}
// Ab PING ka: +PONG\r\n banana Main ka kaam nahi hai.