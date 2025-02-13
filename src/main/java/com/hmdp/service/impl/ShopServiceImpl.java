package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryShopById(Long id) {
//        1. handle cache penetration by caching empty string
//        Shop shop = queryShopWithCachePenetration(id);
//        Shop shop = cacheClient
//                .getShopHandleCachePenetration(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

//        2. handle cache breakdown with mutex
//        Shop shop = queryShopWithMutex(id);

//        3. handle cache breakdown with logical expired
//        Shop shop = cacheClient
//                .getShopHandleCacheBreakdownWithLogicalExpiration(
//                        CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES, LOCK_SHOP_KEY);
        Shop shop = cacheClient
                .getShopHandleCacheBreakdownWithLogicalExpiration(
                        CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS, LOCK_SHOP_KEY);
//        Shop shop = queryShopWithLogicalExpired(id);

        if (shop == null) {
            return Result.fail("shop is null");
        }
        return Result.ok(shop);
    }

//    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryShopWithLogicalExpired(Long id) {
//        String shopCacheKey = CACHE_SHOP_KEY + id;
////        1. query shop cache from redis
//        String shopCacheJson = stringRedisTemplate.opsForValue().get(shopCacheKey);
////        2. check if exists
//        if(StrUtil.isBlank(shopCacheJson)){
//            //        3. if exists, return null
//            return null;
//        }
//
////        4. cache hit, deserialize JSON into an object
//        RedisData redisData = JSONUtil.toBean(shopCacheJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
////        5. check if it is expired
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //        5.1 if not, return shop data
//            return shop;
//        }
////        5.2 if expired, need to rebuild the cache
////        6. rebuild the cache
////        6.1 acquire a mutex, check if successful
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
////        6.2 if successful, start a separate thread, rebuild the cache
//        if(isLock){
////            thread pool
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unlock(lockKey);
//                }
//            });
//        }
////        6.3 return the expired data
//        return shop;
//    }

//    handle the cache breakdown issue with a mutex
//    public Shop queryShopWithMutex(Long id) {
//        String shopCacheKey = CACHE_SHOP_KEY + id;
//    //        1. query shop cache from redis
//        String shopCacheJson = stringRedisTemplate.opsForValue().get(shopCacheKey);
//    //        2. check if it exists
//        if(StrUtil.isNotBlank(shopCacheJson)){
//            //        3. if it exists, return data
//            Shop shop = JSONUtil.toBean(shopCacheJson, Shop.class);
//            return shop;
//        }
//
//    //        (cache penetration) check if the cache hit is an empty string ("") or a cache miss(null)
//        if("".equals(shopCacheJson)){
//            return null;
//        }
//
////        implement cache reconstruction
////        1. get the mutex
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
////        2. check if it is successful
//            //        3. if not, sleep and retry
//            if(!isLock){
//                Thread.sleep(50);
//                return queryShopWithMutex(id);
//            }
//
//
////        4. if successful, query the database by id
//            shop = getById(id);
//
////            simulate delay of the cache rebuild
//            Thread.sleep(200);
//
//            //        5. if not exists, return error
//            if (shop == null) {
//        //            to solve the **cache penetration** problem, write the null value(empty string) to redis
//                stringRedisTemplate.opsForValue().set(shopCacheKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //        6. if exists, save it to redis; ** set ttl **
//            stringRedisTemplate.opsForValue().set(shopCacheKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //        release the mutex
//            unlock(lockKey);
//        }
//
//    //        7. return data
//        return shop;
//    }

//    handle the cache penetration issue by storing null values in the cache
//    public Shop queryShopWithCachePenetration(Long id) {
//        String shopCacheKey = CACHE_SHOP_KEY + id;
////        1. query shop cache from redis
//        String shopCacheJson = stringRedisTemplate.opsForValue().get(shopCacheKey);
////        2. check if exists
//        if(StrUtil.isNotBlank(shopCacheJson)){
//            //        3. if exists, return data
//            Shop shop = JSONUtil.toBean(shopCacheJson, Shop.class);
//            return shop;
//        }
//
////        (cache penetration) check if the cache hit is an empty string ("") or a cache miss(null)
//        if("".equals(shopCacheJson)){
//            return null;
//        }
//
////        4. if not exists, query id from database
//        Shop shop = getById(id);
////        5. if not exists, return error
//        if (shop == null) {
////            to solve the **cache penetration** problem, write the null value(empty string) to redis
//            stringRedisTemplate.opsForValue().set(shopCacheKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
////        6. if exists, save it to redis; ** set ttl **
//        stringRedisTemplate.opsForValue().set(shopCacheKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
////        7. return data
//        return shop;
//    }

////    try to acquire a lock
//    private boolean tryLock(String lockKey) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
////    release a lock
//    private void unlock(String lockKey) {
//        stringRedisTemplate.delete(lockKey);
//    }
//
////    cache warming / cache rebuild
//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
////        query shop data
//        Shop shop = getById(id);
////        simulate delay of cache rebuild
//        Thread.sleep(200);
////        encapsulate as logically expired
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
////        write to redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

//    cache update
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateShop(Shop shop) {
//        get the id and check if it is null
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("the shop id is null");
        }
//        write to the db
        updateById(shop);
//        delete the cache
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
//        1. check if we need to query by coordinate
        if(x == null || y == null){
//            no need to query by coordi, can query the db
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
//        2. calculate pagination parameters
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = from + SystemConstants.DEFAULT_PAGE_SIZE;

//        3. query from redis, sort by distance and paginate : shopId, distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

//        4. get id
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());

        if(from >= list.size()){
            return Result.ok(Collections.emptyList());
        }
//        extract from to end
        list.stream().skip(from).forEach(geoResult -> {
            String shopIdStr = geoResult.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));

            Distance distance = geoResult.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

//        5. query shop by id, return shop with distance
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list();

        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shops);
    }
}
