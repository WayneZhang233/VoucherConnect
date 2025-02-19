package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
//        asynchronous, non-blocking, calculate the total time
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println(id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time:" + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        // Query all shop data
        List<Shop> allShops = shopService.list(); // Assuming shopService.list() retrieves all shop records

        // Iterate through all shops and cache each one
        for (Shop shop : allShops) {
            // Set cache using logical expiration method
            cacheClient.setWithLogicalExpired(CACHE_SHOP_KEY + shop.getId(), shop, 10L, TimeUnit.SECONDS);
        }
//    Shop shop = shopService.getById(1L);
//    cacheClient.setWithLogicalExpired(CACHE_SHOP_KEY + shop.getId(), shop, 10L, TimeUnit.SECONDS);
    }


    @Test
    void loadShopData() {
//        1. query shop data
        List<Shop> list = shopService.list();
//        2. group shops by typeId
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
//        3. write each group to redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
//            get key and value
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> shops = entry.getValue();

//            encapsulate shops into geolocations
            List<RedisGeoCommands.GeoLocation<String>> geolocations = new ArrayList<>(shops.size());

            for (Shop shop : shops) {
                geolocations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }

            //            GEOADD key longi lati member
            stringRedisTemplate.opsForGeo().add(key, geolocations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(count);
    }
}
