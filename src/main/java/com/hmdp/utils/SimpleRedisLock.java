package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private final String name;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:";


    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true代表获取锁成功；false代表获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // get thread
        long threadId = Thread.currentThread().getId();
        // lock in redis
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        stringRedisTemplate.delete(LOCK_KEY_PREFIX + name);
    }
}
