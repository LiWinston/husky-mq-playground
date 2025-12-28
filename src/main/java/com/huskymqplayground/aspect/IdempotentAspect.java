package com.huskymqplayground.aspect;

import com.huskymqplayground.annotation.RocketMQIdempotent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final RedissonClient redissonClient;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, RocketMQIdempotent idempotent) throws Throwable {
        // 1. 获取方法参数，寻找 MessageExt
        Object[] args = joinPoint.getArgs();
        MessageExt messageExt = null;
        for (Object arg : args) {
            if (arg instanceof MessageExt) {
                messageExt = (MessageExt) arg;
                break;
            }
        }

        if (messageExt == null) {
            log.warn("@RocketMQIdempotent used on method without MessageExt argument, skipping check.");
            return joinPoint.proceed();
        }

        // 2. 获取 Message Key
        String keys = messageExt.getKeys();
        if (keys == null || keys.isEmpty()) {
            log.warn("Message Key is empty, skipping idempotency check. MsgId: {}", messageExt.getMsgId());
            return joinPoint.proceed();
        }

        // 3. 构建 Redis Key
        String redisKey = idempotent.prefix() + keys;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);

        // 4. 尝试加锁 (SETNX)
        boolean isNew = bucket.setIfAbsent("1", Duration.of(idempotent.expire(), idempotent.timeUnit().toChronoUnit()));

        if (!isNew) {
            log.warn("Duplicate message detected, skip processing. Key: {}, MsgId: {}", keys, messageExt.getMsgId());
            // 直接返回 null，视为消费成功（RocketMQ 认为只要不抛异常就是成功）
            return null;
        }

        // 5. 执行业务逻辑
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 6. 异常处理：删除 Key 以便重试
            log.error("Process failed, deleting idempotent key. Key: {}", keys, e);
            bucket.delete();
            throw e;
        }
    }
}
