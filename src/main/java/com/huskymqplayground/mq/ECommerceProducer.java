package com.huskymqplayground.mq;

import com.huskymqplayground.dto.CartDTO;
import com.huskymqplayground.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ECommerceProducer {

    private final RocketMQTemplate rocketMQTemplate;

    // 定义 Producer Group 常量，需与 Listener 中的 txProducerGroup 对应
    public static final String CART_TX_GROUP = "cart-tx-group";
    public static final String ORDER_TX_GROUP = "order-tx-group";

    /**
     * 发送购物车事务消息
     */
    public void sendTransactionalCart(CartDTO cartDTO) {
        String topic = "cart-transaction-topic";
        
        Message<CartDTO> message = MessageBuilder.withPayload(cartDTO)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, cartDTO.getTraceId())
                .build();

        // 指定 txProducerGroup 发送事务消息
        SendResult sendResult = rocketMQTemplate.sendMessageInTransaction(CART_TX_GROUP, topic, message, null);
        log.info("Send transactional cart result: {}, msgId: {}", sendResult.getSendStatus(), sendResult.getMsgId());
    }

    /**
     * 发送订单事务消息
     */
    public void sendTransactionalOrder(OrderDTO orderDTO) {
        String topic = "order-transaction-topic";

        Message<OrderDTO> message = MessageBuilder.withPayload(orderDTO)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, orderDTO.getTraceId())
                .build();

        // 指定 txProducerGroup 发送事务消息
        SendResult sendResult = rocketMQTemplate.sendMessageInTransaction(ORDER_TX_GROUP, topic, message, null);
        log.info("Send transactional order result: {}, msgId: {}", sendResult.getSendStatus(), sendResult.getMsgId());
    }
}
