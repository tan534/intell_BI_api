package com.intell_BI_backend.manager;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisLimitManagerTest {

    @Resource
    RedisLimitManager redisLimitManager;

    @Test
    void test() {
        String userid ="1";
        for (int i = 0; i < 5; i++) {
            redisLimitManager.doRateLimit(userid);
            System.out.println("成功");
        }
    }



}