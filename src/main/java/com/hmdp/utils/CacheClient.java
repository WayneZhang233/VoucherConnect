package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

// cache utils
@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
    }

    public void setWithLogicalExpired(String key, Object value, Long expireTime, TimeUnit timeUnit) {
//      set logical expiration
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
//      write to redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R getShopHandleCachePenetration(String keyPrefix, ID id, Class<R> type,
                                                   Function<ID, R> dbFallback, Long expireTime, TimeUnit timeUnit) {
        String cacheKey = keyPrefix + id;
//        1. query shop cache from redis
        String cacheJson = stringRedisTemplate.opsForValue().get(cacheKey);
//        2. check if exists
        if(StrUtil.isNotBlank(cacheJson)){
            //        3. if exists, return data
            R r = JSONUtil.toBean(cacheJson, type);
            return r;
        }

//        (cache penetration) check if the cache hit is an empty string ("") or a cache miss(null)
        if("".equals(cacheJson)){
            return null;
        }

//        4. if not exists, query id from database
        R r = dbFallback.apply(id);
//        5. if not exists, return error
        if (r == null) {
//            to solve the **cache penetration** problem, write the null value(empty string) to redis
            stringRedisTemplate.opsForValue().set(cacheKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//        6. if exists, save it to redis; ** set ttl **
        this.set(cacheKey, r, expireTime, timeUnit);
//        7. return data
        return r;
    }

    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R getShopHandleCacheBreakdownWithLogicalExpiration(String keyPrefix, ID id, Class<R> type,
                                                                      Function<ID, R> dbFallback, Long time, TimeUnit timeUnit,
                                                                      String mutexKeyPrefix) {
        String cacheKey = keyPrefix + id;
//        1. query shop cache from redis
        String cacheJson = stringRedisTemplate.opsForValue().get(cacheKey);
//        2. check if exists
        if(StrUtil.isBlank(cacheJson)){
            //        3. if exists, return null
            return null;
        }

//        4. cache hit, deserialize JSON into an object
        RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
//        5. check if it is expired
        if(expireTime.isAfter(LocalDateTime.now())){
            //        5.1 if not, return shop data
            return r;
        }
//        5.2 if expired, need to rebuild the cache
//        6. rebuild the cache
//        6.1 acquire a mutex, check if successful
        String lockKey = mutexKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
//        6.2 if successful, start a separate thread, rebuild the cache
        if(isLock){
//            thread pool
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
//                    query db
                    R r1 = dbFallback.apply(id);
//                    write to redis
                    this.setWithLogicalExpired(cacheKey, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
//        6.3 return the expired data
        return r;
    }

    //    try to acquire a lock
    private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //    release a lock
    private void unlock(String lockKey) {
        stringRedisTemplate.delete(lockKey);
    }
}
