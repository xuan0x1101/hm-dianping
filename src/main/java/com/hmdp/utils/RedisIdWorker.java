package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    // (2000, 1, 1, 1, 1, 1)
    private static final long BASE_TIMESTAMP = 946688461L;

    private static final int COUNT_BITS = 32;

    @Resource
    StringRedisTemplate stringRedisTemplate;


    public long nextId(String keyPrefix) {
        // time stamp
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BASE_TIMESTAMP;

        // seq
        String date = now.format(DateTimeFormatter.ofPattern("yyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timeStamp << COUNT_BITS | count;
    }
}
