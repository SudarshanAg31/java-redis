package command;
import java.util.List;
public interface  command {
    byte[]execute(List<String>args);
}
//Mujhe arguments do, main RESP response ke bytes return karungi.