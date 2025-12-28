package com.huskymqplayground.mq;

import com.huskymqplayground.domain.UserLog;
import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mapper.UserLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(topic = "user-log-topic", consumerGroup = "husky-consumer-group")
@RequiredArgsConstructor
public class AsyncSaveConsumer implements RocketMQListener<UserLogDTO> {

    private final UserLogMapper userLogMapper;

    @Override
    public void onMessage(UserLogDTO message) {
        log.info("Received message. TraceId: {}, Payload: {}", message.getTraceId(), message);
        
        // 幂等性校验示例 (实际生产中通常使用 Redis 或 数据库唯一索引)
        // if (redis.exists(message.getTraceId())) {
        //     log.warn("Duplicate message detected. TraceId: {}", message.getTraceId());
        //     return;
        // }

        UserLog userLog = new UserLog();
        userLog.setUsername(message.getUsername());
        userLog.setOperation(message.getOperation());
        userLog.setCreateTime(LocalDateTime.now());
        
        userLogMapper.insert(userLog);
        log.info("Saved user log to database. ID: {}", userLog.getId());
    }
}
