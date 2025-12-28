package com.huskymqplayground.mq;

import com.huskymqplayground.annotation.RocketMQIdempotent;
import com.huskymqplayground.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = "order-transaction-topic",
    consumerGroup = "husky-order-consumer-v3",
     consumeMode = ConsumeMode.ORDERLY
)
@ConditionalOnProperty(prefix = "rocketmq.consumer.switch.Order", name = "v3", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OrderConsumerV3 extends BaseRocketMQListener<OrderDTO> {

    @Override
    @RocketMQIdempotent(prefix = "mq:idempotent:order:v3:")
    public void onMessage(MessageExt messageExt) {
        super.onMessage(messageExt);
    }

    @Override
    protected void handleMessage(OrderDTO dto, MessageExt messageExt) {
        log.info("[Order-V3] Received order message. Keys: {}, OrderNo: {}, Buyer: {}, Item: {}, Qty: {}, Amount: {}",
                messageExt.getKeys(), dto.getOrderNo(), dto.getBuyer(), dto.getItemName(), dto.getQuantity(), dto.getAmount());
    }
}
