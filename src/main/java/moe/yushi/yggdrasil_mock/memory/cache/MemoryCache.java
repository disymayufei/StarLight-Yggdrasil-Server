package moe.yushi.yggdrasil_mock.memory.cache;

import java.util.LinkedList;

public abstract class MemoryCache<T> {
    protected LinkedList<T> cache;
    protected final int capacity;

    protected volatile int size = 0;

    public MemoryCache(int capacity) {
        if (capacity <= 0) {
            capacity = 1;
        }

        this.capacity = capacity;
        this.cache = new LinkedList<>();
    }

    public int getSize() {
        return size;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isFull() {
        return size >= capacity;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public synchronized void add(T ele) {
        if (size + 1 > capacity) {
            cache.removeFirst();
            cache.add(ele);
        }
        else {
            cache.add(ele);
            size++;
        }
    }

    public synchronized void remove(T ele) {
        if (cache.remove(ele)) {
            size--;
        }
    }

    public synchronized void clear() {
        if (!cache.isEmpty()) {
            cache = new LinkedList<>();
        }

        size = 0;
    }
}
