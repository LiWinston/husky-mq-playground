package com.huskymqplayground.mq;

import com.huskymqplayground.domain.UserLog;
import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mapper.UserLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(topic = "user-log-topic", consumerGroup = "husky-consumer-group")
@RequiredArgsConstructor
public class AsyncSaveConsumer implements RocketMQListener<UserLogDTO> {

    private final UserLogMapper userLogMapper;
    private final RedissonClient redissonClient;

    @Override
    public void onMessage(UserLogDTO message) {
        log.info("Received message. TraceId: {}, Payload: {}", message.getTraceId(), message);
        
        // 幂等性校验：使用 Redisson 的原子操作 setIfAbsent (SETNX)
        // Key 格式: "mq:idempotent:{traceId}"
        String idempotentKey = "mq:idempotent:" + message.getTraceId();
        RBucket<String> bucket = redissonClient.getBucket(idempotentKey);
        
        // 尝试设置 Key，如果 Key 已存在则返回 false，表示重复消费
        // 设置过期时间为 24 小时，避免 Key 永久堆积
        boolean isNew = bucket.setIfAbsent("1", Duration.ofHours(24));
        
        if (!isNew) {
            log.warn("Duplicate message detected, skip processing. TraceId: {}", message.getTraceId());
            return;
        }

        try {
            UserLog userLog = new UserLog();
            userLog.setUsername(message.getUsername());
            userLog.setOperation(message.getOperation());
            userLog.setCreateTime(LocalDateTime.now());
            
            userLogMapper.insert(userLog);
            log.info("Saved user log to database. ID: {}", userLog.getId());
        } catch (Exception e) {
            // 如果业务处理失败，需要删除幂等 Key，以便重试
            // 注意：这里取决于业务策略。如果是系统异常可以删 Key 重试；如果是数据错误则不应重试。
            // 简单起见，这里演示删除 Key
            bucket.delete();
            log.error("Process failed, deleted idempotent key. TraceId: {}", message.getTraceId(), e);
            throw e; // 抛出异常让 RocketMQ 重试
        }
    }
}
