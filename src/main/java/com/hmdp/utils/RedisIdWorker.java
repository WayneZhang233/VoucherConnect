package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    public static final long BEGIN_TIMESTAMP = 1735689600L;
//    serial number length
    public static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
//        1. generate timestamp
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
//        2. generate serial number
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
//        auto increment
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date, timeStamp);
//        3. concatenate
        return timeStamp << COUNT_BITS | count;
    }

//
//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }
}
