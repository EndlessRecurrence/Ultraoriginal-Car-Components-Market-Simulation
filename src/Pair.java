import java.io.Serializable;

public class Pair<V, W> implements Serializable {
    private V first;
    private W second;

    public Pair(V first, W second) {
        this.first = first;
        this.second = second;
    }

    public V getFirst() {
        return first;
    }

    public void setFirst(V first) {
        this.first = first;
    }

    public W getSecond() {
        return second;
    }

    public void setSecond(W second) {
        this.second = second;
    }
}
