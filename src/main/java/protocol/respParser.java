package protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
public class respParser {
    public static List<String> parse(InputStream input)throws IOException {
        BufferedReader reader =new BufferedReader(new InputStreamReader(input));
        String firstLine = reader.readLine();
        if (firstLine == null) {
            return null;
        }
        if (!firstLine.startsWith("*")) {
            return null;
        }
        int numberOfArguments =Integer.parseInt(firstLine.substring(1));
        List<String> arguments =new ArrayList<>();
        for (int i = 0; i < numberOfArguments; i++) {
            String lengthLine = reader.readLine();
            if (lengthLine == null ||!lengthLine.startsWith("$")) {
                return null;
            }
            int length = Integer.parseInt(lengthLine.substring(1));
            String value = reader.readLine();
            if (value == null || value.length() != length) {
                return null;
            }
            arguments.add(value);
        }
        return arguments;
    }
}