package protocol;

import java.nio.charset.StandardCharsets;
public class resp {
    public static byte[] simpleString(String value) {
        return ("+" + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
    public static byte[] bulkString(String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        String header = "$" + data.length + "\r\n";
        byte[] headerBytes =header.getBytes(StandardCharsets.UTF_8);
        byte[] result =new byte[headerBytes.length + data.length + 2];
        System.arraycopy(headerBytes, 0,result, 0,headerBytes.length);
        System.arraycopy(data, 0,result, headerBytes.length,data.length);
        result[result.length - 2] = '\r';
        result[result.length - 1] = '\n';
        return result;
    }
    public static byte[] error(String message) {
        return ("-" + message + "\r\n").getBytes(StandardCharsets.UTF_8);
    }
}