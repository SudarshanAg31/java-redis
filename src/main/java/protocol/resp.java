package protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
public class resp {
    public static byte[] simpleString(String value) {
        return ("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
    public static byte[] integer(long value) {
    return (":" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
    public static byte [] array(List<String> element) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
            out.writeBytes(("*"+element.size()+"\r\n").getBytes(StandardCharsets.UTF_8));
            for(String elem: element){
                out.writeBytes(bulkString(elem));
            }
        return out.toByteArray();
    }
    public static byte[] bulkString(String value) {//foo
        byte[] data = value.getBytes(StandardCharsets.UTF_8);//[102,111,111]
        String header = "$" + data.length + "\r\n";//$3\r\n
        byte[] headerBytes =header.getBytes(StandardCharsets.UTF_8);//[36,51,13,10]
        byte[] result =new byte[headerBytes.length + data.length + 2];//4+3+2=9
        System.arraycopy(headerBytes, 0,result, 0,headerBytes.length);//[36,51,13,10,0,0,0,0,0]
        System.arraycopy(data, 0,result, headerBytes.length,data.length);//[36,51,13,10,102,111,111,0,0]
        result[result.length - 2] = '\r';//[36,51,13,10,102,111,111,13,0]
        result[result.length - 1] = '\n';//[36,51,13,10,102,111,111,13,10]
        return result;
    }
    public static byte[] nullBulkString() {               
        return "$-1\r\n".getBytes(StandardCharsets.UTF_8);//-1 represent the null string 
    }
    public static byte[] error(String message) {
        return ("-" + message + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}