package com.huskymqplayground.mq;

import com.huskymqplayground.dto.UserLogDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncSaveProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public void sendUserLog(UserLogDTO userLogDTO) {
        // Topic: user-log-topic
        String topic = "user-log-topic";
        
        // 构建消息，设置 Keys (业务唯一标识)
        // 最佳实践：Keys 应该唯一且方便查询，如订单号、流水号、UUID
        org.springframework.messaging.Message<UserLogDTO> message = MessageBuilder.withPayload(userLogDTO)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, userLogDTO.getTraceId())
                .build();

        // Send async message
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("Send message success. msgId: {}, keys: {}", sendResult.getMsgId(), userLogDTO.getTraceId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Send message failed. keys: {}", userLogDTO.getTraceId(), e);
            }
        });
    }

    /**
     * 发送事务消息
     * 流程：
     * 1. 发送 Half Message (半消息) 到 MQ
     * 2. MQ 收到后回调 TransactionListener.executeLocalTransaction
     * 3. 根据 executeLocalTransaction 返回值 (COMMIT/ROLLBACK/UNKNOWN) 决定消息是否对消费者可见
     */
    public void sendTransactionalUserLog(UserLogDTO userLogDTO) {
        String topic = "user-log-topic";

        org.springframework.messaging.Message<UserLogDTO> message = MessageBuilder.withPayload(userLogDTO)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, userLogDTO.getTraceId())
                .build();

        // 发送事务消息
        // arg: 传递给 executeLocalTransaction 的参数，这里我们不需要额外参数，传 null
        SendResult sendResult = rocketMQTemplate.sendMessageInTransaction(topic, message, null);

        log.info("Send transactional message result: {}, msgId: {}", sendResult.getSendStatus(), sendResult.getMsgId());
    }

    public void sendOrderedUserLog(UserLogDTO userLogDTO, String hashKey) {
        // Topic: user-log-topic
        String topic = "user-log-topic";

        // 构建消息
        org.springframework.messaging.Message<UserLogDTO> message = MessageBuilder.withPayload(userLogDTO)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, userLogDTO.getTraceId())
                .build();

        // Send ordered message (sync)
        // hashKey 通常使用 userId 或 orderId，确保同一组消息进入同一个 Queue
        rocketMQTemplate.syncSendOrderly(topic, message, hashKey);
        log.info("Send ordered message success. keys: {}, hashKey: {}", userLogDTO.getTraceId(), hashKey);
    }
}
