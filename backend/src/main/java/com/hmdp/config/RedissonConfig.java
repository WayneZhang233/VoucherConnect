package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
//        configure redis
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.11.128:6379").setPassword("zwy020303");
//        create Redisson object
        return Redisson.create(config);
    }
}
