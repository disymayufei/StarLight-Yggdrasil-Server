package moe.yushi.yggdrasil_mock.database.memory;

public final class Timer<T> {
    private long expireTime;
    private final T value;

    public Timer(long expireTime, T value) {
        this.expireTime = expireTime;
        this.value = value;
    }

    public boolean isExpire() {
        return expireTime != -1 && expireTime < System.currentTimeMillis();
    }

    public void neverExpire() {
        this.expireTime = -1;
    }

    public void changeExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Timer<?> timer = (Timer<?>) o;

        return value.equals(timer.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
