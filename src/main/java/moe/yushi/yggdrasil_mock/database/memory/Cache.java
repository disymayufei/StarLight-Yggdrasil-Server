package moe.yushi.yggdrasil_mock.database.memory;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weighers;

import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Cache<T> {
    private final ConcurrentLinkedHashMap<String, Timer<T>> cache =
            new ConcurrentLinkedHashMap.Builder<String, Timer<T>>()
            .maximumWeightedCapacity(8192)
            .weigher(Weighers.singleton())
            .build();

    /**
     * 默认过期时长，单位：秒
     */
    public static final long DEFAULT_EXPIRE = 60 * 5;  // 5min

    /**
     * 不设置过期时长
     */
    public static final long NOT_EXPIRE = -1;

    public boolean existsKey(String key) {
        return cache.containsKey(key);
    }

    /**
     * 重名名key，如果newKey已经存在，则newKey的原值被覆盖
     *
     * @param oldKey 原key值
     * @param newKey 新key值
     */
    public void renameKey(String oldKey, String newKey) {
        if (cache.containsKey(oldKey)) {
            cache.put(newKey, cache.get(oldKey));
            cache.remove(oldKey);
        }
    }

    /**
     * 重名名key，且仅在新key值不存在时才重命名
     *
     * @param oldKey 原key值
     * @param newKey 新key值
     * @return 修改成功返回true
     */
    public boolean renameKeyNotExist(String oldKey, String newKey) {
        if (cache.containsKey(oldKey)) {
            if (!cache.containsKey(newKey)) {
                cache.put(newKey, cache.get(oldKey));
                cache.remove(oldKey);
                return true;
            }
        }

        return false;
    }

    /**
     * 删除key
     *
     * @param key 待删除的key值
     */
    public void deleteKey(String key) {
        cache.remove(key);
    }

    /**
     * 删除多个key
     *
     * @param keys 待删除的一组key值
     */
    public void deleteKey(String... keys) {
        Stream.of(keys).forEach(cache::remove);
    }

    /**
     * 删除Key的集合
     *
     * @param keys 待删除的key集合
     */
    public void deleteKey(Collection<String> keys) {
        keys.forEach(cache::remove);
    }

    /**
     * 设置key的生命周期
     *
     * @param key 待设置的key值
     * @param time 生命周期的时间值
     * @param timeUnit 生命周期的时间单位
     */
    public void expireKey(String key, long time, TimeUnit timeUnit) {
        if (cache.containsKey(key)) {
            if (time < 0) {
                cache.get(key).neverExpire();
            }
            else {
                Timer<T> value = cache.get(key);
                value.changeExpireTime(value.getExpireTime() + timeUnit.toMillis(time));
            }
        }
    }

    /**
     * 指定key在指定的日期过期
     *
     * @param key 待设置的key值
     * @param date 过期的日期
     */
    public void expireKeyAt(String key, Date date) {
        if (cache.containsKey(key)) {
            Timer<T> value = cache.get(key);
            value.changeExpireTime(date.getTime());
        }
    }

    /**
     * 查询key的生命周期
     *
     * @param key 待查询的key值
     * @return 对应时间单位的生命周期值
     */
    public long getKeyExpire(String key) {
        if (cache.containsKey(key)) {
            return cache.get(key).getExpireTime();
        }
        else {
            return 0;
        }
    }

    /**
     * 将key设置为永久有效
     *
     * @param key key值
     */
    public void persistKey(String key) {
        cache.get(key).neverExpire();
    }


    public void put(String key, T value) {
        var timer = new Timer<>(System.currentTimeMillis() + DEFAULT_EXPIRE * 1000, value);
        cache.put(key, timer);
    }

    public T get(String key) {
        var value = cache.get(key);
        if (value != null && value.isExpire()) {
            cache.remove(key);
            return null;
        }

        return value != null ? value.getValue() : null;
    }
}
