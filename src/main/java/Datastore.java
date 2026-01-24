
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

        if (valueEntry == null){
            return null;
        }
        System.out.println(currentTimeStamp.toString());
        
        //Infinite expiry, cus not set in the additional PX argument
        if(valueEntry.expiryTime == -1L){
            return valueEntry.value;
        }

        //Checking for expiry
        Long net = valueEntry.expiryTime + valueEntry.setAt;
        System.out.println(valueEntry.expiryTime.toString());
        System.out.println(net.toString());
        if (net > currentTimeStamp){
            return valueEntry.value; //within expiry date. So can.
        }

        //Expiry case
        keyValueStore.remove(key);
        return null;
    }

    public static Integer incrementKey(String key){
        ValueEntry valueEntry = keyValueStore.get(key);
        if (valueEntry != null){
            try{
                int current = Integer.parseInt((String) valueEntry.value);
                valueEntry.value = String.valueOf(current + 1); //ALWAYS make sure to store BACK AS STRING, not any other data type. --> Redis works as such: Store as string --> convert, increment, or do any other neccesary type-related operation --> store BACK as string!!!
                return current + 1;
            }
            catch(NumberFormatException e){
                return -1;
            }
        }
        setKeyValue(key, "1", -1L);
        return 1;  
    }
}