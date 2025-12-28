package com.huskymqplayground.mq;

import com.huskymqplayground.domain.UserLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskymqplayground.annotation.RocketMQIdempotent;
import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mapper.UserLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(topic = "user-log-topic", consumerGroup = "husky-consumer-group-v2")
@ConditionalOnProperty(prefix = "rocketmq.consumer.switch.AsyncSave", name = "v2", havingValue = "true")
@RequiredArgsConstructor
public class AsyncSaveConsumerV2 implements RocketMQListener<MessageExt> {

    private final UserLogMapper userLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @RocketMQIdempotent // 开启幂等性校验
    public void onMessage(MessageExt messageExt) {
        try {
            // 1. 手动反序列化 Body
            String json = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            UserLogDTO message = objectMapper.readValue(json, UserLogDTO.class);
            
            log.info("[V2-AOP] Received message. Keys: {}, Payload: {}", messageExt.getKeys(), message);

            // 2. 业务逻辑
            UserLog userLog = new UserLog();
            userLog.setUsername(message.getUsername());
            userLog.setOperation(message.getOperation() + "_V2"); // 标记 V2 处理
            userLog.setCreateTime(LocalDateTime.now());
            
            userLogMapper.insert(userLog);
            log.info("[V2-AOP] Saved user log to database. ID: {}", userLog.getId());
            
        } catch (Exception e) {
            log.error("[V2-AOP] Consume failed.", e);
            throw new RuntimeException(e); // 抛出异常以触发重试
        }
    }
}
