package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 设置缓存
     * @param key
     * @param val
     * @param time
     * @param unit
     */
    public void set(String key, Object val, Integer time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(val), time, unit);
    }


    /**
     * 设置逻辑过期缓存
     * @param key
     * @param val
     * @param time
     * @param unit
     */
    public void setLogicalExpire(String key, Object val, Integer time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(val);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 解决缓存穿透
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R getPassThrough(
            String keyPrefix, ID id,
            Class<R> type, Function<ID, R> dbFallback,
            Integer time, TimeUnit unit
    ) {
        String redisKey = keyPrefix + id;
        // get from redis
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        // if exist
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // not exist in DB
        // 解决缓存穿透
        if (json != null) {
            return null;
        }

        // get from db
        R r = dbFallback.apply(id);

        if (r == null) {
            // 解决缓存穿透
            stringRedisTemplate.opsForValue().set(redisKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // save in redis
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(r), time, unit);

        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 获取线程锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放线程锁
    private void unLock(String key) {
        Boolean flag = stringRedisTemplate.delete(key);
    }


    /**
     * 缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @param lockKeyPrefix
     * @return
     * @param <R>
     * @param <ID>
     */
    public  <R, ID> R getLogicalExpire(
            String keyPrefix, ID id,
            Class<R> type, Function<ID, R> dbFallback,
            Integer time, TimeUnit unit, String lockKeyPrefix
    ) {
        String redisKey = keyPrefix + id;
        // get from redis
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        // if shop not exist
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // hit, judge if expire
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // not expire
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // expire
        /*
         redis rebuild
        */
        // get lock
        String lockKey = lockKeyPrefix + id;
        boolean gotLock = tryLock(lockKey);
        if (gotLock) {
            // success, retry redis
            json = stringRedisTemplate.opsForValue().get(redisKey);
            redisData = JSONUtil.toBean(json, RedisData.class);
            // if already updated
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return JSONUtil.toBean((JSONObject) redisData.getData(), type);
            }

            // fail, rebuild
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    setLogicalExpire(redisKey, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }

        return r;
    }
}
