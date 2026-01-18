
import java.util.HashMap;

public class Datastore{
    private static final HashMap<Object, ValueEntry> keyValueStore = new HashMap(); //Fast, unordered map from Java.util class

    private static Datastore datastoreInstance = null;

    private Datastore(){

    }

    public static synchronized Datastore getInstance(){
        if(datastoreInstance == null){
            datastoreInstance = new Datastore();
        }
        return datastoreInstance;
    }

    public static synchronized void setKeyValue(String key, Object value, Long expiryTime){
        Long setAt = System.currentTimeMillis();
        ValueEntry valueEntry = new ValueEntry(value, setAt, expiryTime);
        keyValueStore.put(key, valueEntry);
    }

    public static synchronized Object getKeyValue(String key){

        Long currentTimeStamp = System.currentTimeMillis();
        ValueEntry valueEntry = keyValueStore.get(key);
        System.out.println(currentTimeStamp.toString());
        
        if(valueEntry.expiryTime == -1L){
            return valueEntry.value;
        }

        Long net = valueEntry.expiryTime + valueEntry.setAt;
        System.out.println(valueEntry.expiryTime.toString());
        System.out.println(net.toString());
        if (net > currentTimeStamp){
            return valueEntry.value;
        }
        keyValueStore.remove(key);
        return null;
    }

    public static Integer incrementKey(String key){
        ValueEntry valueEntry = keyValueStore.get(key);
        if (valueEntry != null){
            if (valueEntry.value instanceof Integer){
                valueEntry.value += 1;
                return 0;
            }
            else{
                return -1;
            }
        }
        setKeyValue(key, 1, -1L);
        return 1;  
    }
}