

public class ValueEntry{
    Object value;
    Long setAt;
    Long expiryTime;

    public ValueEntry(Object value, Long setAt, Long expiryTime){
        this.value = value;
        this.setAt = setAt;
        this.expiryTime = expiryTime;
    }
}