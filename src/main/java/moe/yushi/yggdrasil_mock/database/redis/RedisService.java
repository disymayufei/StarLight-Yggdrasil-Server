package moe.yushi.yggdrasil_mock.database.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RedisService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 默认过期时长，单位：秒
     */
    public static final long DEFAULT_EXPIRE = 60 * 5;  // 5min

    /**
     * 不设置过期时长
     */
    public static final long NOT_EXPIRE = -1;

    public boolean existsKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 重名名key，如果newKey已经存在，则newKey的原值被覆盖
     *
     * @param oldKey 原key值
     * @param newKey 新key值
     */
    public void renameKey(String oldKey, String newKey) {
        redisTemplate.rename(oldKey, newKey);
    }

    /**
     * 重名名key，且仅在新key值不存在时才重命名
     *
     * @param oldKey 原key值
     * @param newKey 新key值
     * @return 修改成功返回true
     */
    public boolean renameKeyNotExist(String oldKey, String newKey) {
        return Boolean.TRUE.equals(redisTemplate.renameIfAbsent(oldKey, newKey));
    }

    /**
     * 删除key
     *
     * @param key 待删除的key值
     */
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 删除多个key
     *
     * @param keys 待删除的一组key值
     */
    public void deleteKey(String... keys) {
        Set<String> kSet = Stream.of(keys).collect(Collectors.toSet());
        redisTemplate.delete(kSet);
    }

    /**
     * 删除Key的集合
     *
     * @param keys 待删除的key集合
     */
    public void deleteKey(Collection<String> keys) {
        Set<String> kSet = new HashSet<>(keys);
        redisTemplate.delete(kSet);
    }

    /**
     * 设置key的生命周期
     *
     * @param key 待设置的key值
     * @param time 生命周期的时间值
     * @param timeUnit 生命周期的时间单位
     */
    public void expireKey(String key, long time, TimeUnit timeUnit) {
        redisTemplate.expire(key, time, timeUnit);
    }

    /**
     * 指定key在指定的日期过期
     *
     * @param key 待设置的key值
     * @param date 过期的日期
     */
    public void expireKeyAt(String key, Date date) {
        redisTemplate.expireAt(key, date);
    }

    /**
     * 查询key的生命周期
     *
     * @param key 待查询的key值
     * @param timeUnit 时间单位
     * @return 对应时间单位的生命周期值
     */
    public long getKeyExpire(String key, TimeUnit timeUnit) {
        Long expire = redisTemplate.getExpire(key, timeUnit);
        return expire == null ? 0 : expire;
    }

    /**
     * 将key设置为永久有效
     *
     * @param key key值
     */
    public void persistKey(String key) {
        redisTemplate.persist(key);
    }


    public void put(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}
