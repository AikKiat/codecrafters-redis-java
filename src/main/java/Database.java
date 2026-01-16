
import java.util.HashMap;

public class Database{
    private static final HashMap<Object, Object> keyValueStore = new HashMap(); //Fast, unordered map from Java.util class

    private static Database databaseInstance = null;

    private Database(){

    }

    public static synchronized Database getInstance(){
        if(databaseInstance == null){
            databaseInstance = new Database();
        }
        return databaseInstance;
    }

    public static synchronized void setKeyValue(String key, String value){
        keyValueStore.put(key, value);
    }

    public static synchronized Object getKeyValue(String key){
        Object value = keyValueStore.get(key);
        return value;
    }
}