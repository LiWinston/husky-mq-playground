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

/**
 * V1 版本消费者：手动实现幂等性校验
 * 演示最原始的 setIfAbsent + try-catch-delete 模式
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "user-log-topic", consumerGroup = "husky-consumer-group-v1")
@RequiredArgsConstructor
public class AsyncSaveConsumerV1 implements RocketMQListener<UserLogDTO> {

    private final UserLogMapper userLogMapper;
    private final RedissonClient redissonClient;

    @Override
    public void onMessage(UserLogDTO message) {
        log.info("[V1-Manual] Received message. TraceId: {}, Payload: {}", message.getTraceId(), message);
        
        // 1. 幂等性校验：使用 Redisson 的原子操作 setIfAbsent (SETNX)
        // Key 格式: "mq:idempotent:v1:{traceId}" (注意区分 V1 V2 的 Key)
        String idempotentKey = "mq:idempotent:v1:" + message.getTraceId();
        RBucket<String> bucket = redissonClient.getBucket(idempotentKey);
        
        // 尝试设置 Key，如果 Key 已存在则返回 false，表示重复消费
        boolean isNew = bucket.setIfAbsent("1", Duration.ofHours(24));
        
        if (!isNew) {
            log.warn("[V1-Manual] Duplicate message detected, skip processing. TraceId: {}", message.getTraceId());
            return;
        }

        try {
            // 2. 业务逻辑
            UserLog userLog = new UserLog();
            userLog.setUsername(message.getUsername());
            userLog.setOperation(message.getOperation() + "_V1"); // 标记 V1 处理
            userLog.setCreateTime(LocalDateTime.now());
            
            userLogMapper.insert(userLog);
            log.info("[V1-Manual] Saved user log to database. ID: {}", userLog.getId());
        } catch (Exception e) {
            // 3. 异常处理：删除 Key 以便重试
            bucket.delete();
            log.error("[V1-Manual] Process failed, deleted idempotent key. TraceId: {}", message.getTraceId(), e);
            throw new RuntimeException(e); // 抛出异常让 RocketMQ 重试
        }
    }
}
