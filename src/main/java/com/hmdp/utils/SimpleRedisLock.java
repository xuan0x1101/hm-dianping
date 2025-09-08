package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private final String name;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:";
    private static final String LOCK_ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


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
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY_PREFIX + name, LOCK_ID_PREFIX + threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_KEY_PREFIX + name),
                LOCK_ID_PREFIX + Thread.currentThread().getId()
        );
//        String thread = LOCK_ID_PREFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(LOCK_KEY_PREFIX + name);
//        // 防止线程阻塞导致误删其他线程锁
//        if (thread.equals(id)) {
//            stringRedisTemplate.delete(LOCK_KEY_PREFIX + name);
//        }
    }
}
