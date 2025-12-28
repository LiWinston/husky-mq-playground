package com.huskymqplayground.mq;

import com.huskymqplayground.dto.OrderDTO;
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
     * 订单事务消息
     * 1. 发送 Half Message 到 MQ
     * 2. Broker 回调 OrderTransactionListener.executeLocalTransaction 执行本地下单
     * 3. 返回 COMMIT/ROLLBACK/UNKNOWN 决定消息是否投递给消费者
     */
    public void sendTransactionalOrder(OrderDTO orderDTO) {
        String topic = "order-transaction-topic";

        org.springframework.messaging.Message<OrderDTO> message = MessageBuilder.withPayload(orderDTO)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, orderDTO.getTraceId())
                .build();

        SendResult sendResult = rocketMQTemplate.sendMessageInTransaction(topic, message, null);
        log.info("Send transactional order result: {}, msgId: {}", sendResult.getSendStatus(), sendResult.getMsgId());
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
