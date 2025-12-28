package com.huskymqplayground.mq;

import com.huskymqplayground.annotation.RocketMQIdempotent;
import com.huskymqplayground.domain.UserLog;
import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mapper.UserLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * V3 版本消费者：继承 BaseRocketMQListener
 * 既享受了 DTO 的便利，又拥有 MessageExt 的元数据，还配合了 AOP 幂等
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "user-log-topic",
        consumerGroup = "husky-consumer-group-v3"
)
@RequiredArgsConstructor
public class AsyncSaveConsumerV3 extends BaseRocketMQListener<UserLogDTO> {

    private final UserLogMapper userLogMapper;

    @Override
    @RocketMQIdempotent(prefix = "mq:idempotent:v3:") // 开启幂等，使用 V3 专属前缀
    public void onMessage(MessageExt messageExt) {
        super.onMessage(messageExt);
    }

    @Override
    protected void handleMessage(UserLogDTO message, MessageExt messageExt) {
        log.info("[V3-BaseClass] Received message. Keys: {}, Payload: {}", messageExt.getKeys(), message);

        // 业务逻辑变得非常纯粹
        UserLog userLog = new UserLog();
        userLog.setUsername(message.getUsername());
        userLog.setOperation(message.getOperation() + "_V3");
        userLog.setCreateTime(LocalDateTime.now());

        userLogMapper.insert(userLog);
        log.info("[V3-BaseClass] Saved user log to database. ID: {}", userLog.getId());
    }
}
