
import java.util.ArrayDeque;
import java.util.Collection;

public class Queue<E>{
    private ArrayDeque<E> queue;
    
    public Queue(){
        queue = new ArrayDeque<E>();
    }

    public Queue(Collection<? extends E> collection){
        queue = new ArrayDeque<E>(collection);
    }

    public Queue(Integer size){
        queue = new ArrayDeque<E>(size);
    }

    public boolean add(E element){
        return queue.add(element);
    }

    public void clear(){
        queue.clear();
    }

    public E peek(){
        return queue.peek(); //peek returns but does not remove, the current head of the queue.
    }

    public E popLatest(){
        return queue.poll(); //poll() method retrieves and removes the head of the head.
    }

    public int getSize(){
        return queue.size();
    }
}