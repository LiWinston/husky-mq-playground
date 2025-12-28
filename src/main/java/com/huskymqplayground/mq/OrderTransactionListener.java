package com.huskymqplayground.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskymqplayground.domain.OrderTransaction;
import com.huskymqplayground.dto.OrderDTO;
import com.huskymqplayground.mapper.OrderTransactionMapper;
import com.huskymqplayground.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RocketMQTransactionListener(txProducerGroup = ECommerceProducer.ORDER_TX_GROUP)
@RequiredArgsConstructor
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    private final OrderService orderService;
    private final OrderTransactionMapper orderTransactionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String txId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("[OrderTxListener] Executing local transaction. TxId: {}", txId);

        try {
            String json = new String((byte[]) msg.getPayload(), StandardCharsets.UTF_8);
            OrderDTO orderDTO = objectMapper.readValue(json, OrderDTO.class);

            // 给缺失的订单号兜底，避免 NULL 写库
            if (orderDTO.getOrderNo() == null || orderDTO.getOrderNo().isEmpty()) {
                orderDTO.setOrderNo("ORD-" + UUID.randomUUID());
            }

            if ("rollback".equalsIgnoreCase(orderDTO.getBuyer())) {
                log.warn("[OrderTxListener] Scenario: Rollback. Local transaction will fail.");
                return RocketMQLocalTransactionState.ROLLBACK;
            }

            orderService.createOrderWithTxLog(orderDTO, txId);

            if ("unknown".equalsIgnoreCase(orderDTO.getBuyer())) {
                log.warn("[OrderTxListener] Scenario: Unknown. DB inserted but returning UNKNOWN to trigger check.");
                return RocketMQLocalTransactionState.UNKNOWN;
            }

            log.info("[OrderTxListener] Local transaction committed. OrderNo: {}", orderDTO.getOrderNo());
            return RocketMQLocalTransactionState.COMMIT;

        } catch (Exception e) {
            log.error("[OrderTxListener] Local transaction failed", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String txId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("[OrderTxListener] Checking local transaction status. TxId: {}", txId);

        OrderTransaction tx = orderTransactionMapper.selectById(txId);
        if (tx != null) {
            log.info("[OrderTxListener] Check result: COMMIT (tx log exists). TxId: {}, OrderNo: {}", txId, tx.getOrderNo());
            return RocketMQLocalTransactionState.COMMIT;
        }

        log.warn("[OrderTxListener] Check result: ROLLBACK (tx log missing). TxId: {}", txId);
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
