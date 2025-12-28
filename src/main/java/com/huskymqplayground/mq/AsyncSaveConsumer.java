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
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RocketMQMessageListener(topic = "user-log-topic", consumerGroup = "husky-consumer-group")
@RequiredArgsConstructor
public class AsyncSaveConsumer implements RocketMQListener<MessageExt> {

    private final UserLogMapper userLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @RocketMQIdempotent // 开启幂等性校验
    public void onMessage(MessageExt messageExt) {
        try {
            // 1. 手动反序列化 Body
            String json = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            UserLogDTO message = objectMapper.readValue(json, UserLogDTO.class);
            
            log.info("Received message. Keys: {}, Payload: {}", messageExt.getKeys(), message);

            // 2. 业务逻辑
            UserLog userLog = new UserLog();
            userLog.setUsername(message.getUsername());
            userLog.setOperation(message.getOperation());
            userLog.setCreateTime(LocalDateTime.now());
            
            userLogMapper.insert(userLog);
            log.info("Saved user log to database. ID: {}", userLog.getId());
            
        } catch (Exception e) {
            log.error("Consume failed.", e);
            throw new RuntimeException(e); // 抛出异常以触发重试
        }
    }
}
