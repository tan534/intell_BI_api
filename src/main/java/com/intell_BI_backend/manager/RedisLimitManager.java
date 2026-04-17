package com.intell_BI_backend.manager;


import com.intell_BI_backend.common.ErrorCode;
import com.intell_BI_backend.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 *专门提供 RedisLimiter 限流基础服务的通用类
 **/
@Component
public class RedisLimitManager {

    @Resource
    private RedissonClient redissonClient;

    /**
     *限流操作
     *
     * @param key  区分不同的限流器，比如根据用户id不同分别统计
     **/
    public void doRateLimit(String key) {
        //创建一个名称为user_limiter的限流器，每秒最多访问3次
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, 3, 1, RateIntervalUnit.SECONDS);

        //每当用户点击一次提交，请求一次令牌
        boolean canGen = rateLimiter.tryAcquire(1);
        if(!canGen){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST_ERROR);
        }
    }
}
