package com.huskymqplayground.mq;

import com.huskymqplayground.annotation.RocketMQIdempotent;
import com.huskymqplayground.dto.CartDTO;
import com.huskymqplayground.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = "cart-transaction-topic",
    consumerGroup = "husky-cart-consumer-group"
)
@RequiredArgsConstructor
public class CartConsumer extends BaseRocketMQListener<CartDTO> {

    private final ECommerceProducer eCommerceProducer;

    @Override
    @RocketMQIdempotent(prefix = "mq:idempotent:cart:")
    public void onMessage(MessageExt messageExt) {
        super.onMessage(messageExt);
    }

    @Override
    protected void handleMessage(CartDTO cartDTO, MessageExt messageExt) {
        log.info("[CartConsumer] Received cart message. Keys: {}, User: {}, Item: {}", 
                messageExt.getKeys(), cartDTO.getUsername(), cartDTO.getItemName());

        // 触发下游业务：创建订单 (也是一个事务消息)
        OrderDTO orderDTO = new OrderDTO();
        orderDTO.setTraceId(cartDTO.getTraceId()); // 传递 TraceId
        orderDTO.setBuyer(cartDTO.getUsername());
        orderDTO.setItemName(cartDTO.getItemName());
        orderDTO.setQuantity(cartDTO.getQuantity());
        
        // 模拟价格计算
        BigDecimal price = new BigDecimal("99.00");
        orderDTO.setAmount(price.multiply(BigDecimal.valueOf(cartDTO.getQuantity())));
        
        // 生成订单号
        orderDTO.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8));

        log.info("[CartConsumer] Triggering Order Transaction for OrderNo: {}", orderDTO.getOrderNo());
        eCommerceProducer.sendTransactionalOrder(orderDTO);
    }
}
