package com.huskymqplayground.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskymqplayground.domain.CartTransaction;
import com.huskymqplayground.dto.CartDTO;
import com.huskymqplayground.mapper.CartTransactionMapper;
import com.huskymqplayground.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RocketMQTransactionListener(rocketMQTemplateBeanName = "cartRocketMQTemplate")
@RequiredArgsConstructor
public class CartTransactionListener implements RocketMQLocalTransactionListener {

    private final CartService cartService;
    private final CartTransactionMapper cartTransactionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String txId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("[CartTxListener] Executing local transaction. TxId: {}", txId);

        try {
            String json = new String((byte[]) msg.getPayload(), StandardCharsets.UTF_8);
            CartDTO cartDTO = objectMapper.readValue(json, CartDTO.class);

            // 执行本地事务：保存购物车 + 记录事务日志
            cartService.addToCartWithTxLog(cartDTO, txId);

            if ("unknown".equalsIgnoreCase(cartDTO.getUsername())) {
                log.warn("[CartTxListener] Scenario: Unknown. DB inserted but returning UNKNOWN to trigger check.");
                return RocketMQLocalTransactionState.UNKNOWN;
            }

            log.info("[CartTxListener] Local transaction committed. User: {}, Item: {}", cartDTO.getUsername(), cartDTO.getItemName());
            return RocketMQLocalTransactionState.COMMIT;

        } catch (Exception e) {
            log.error("[CartTxListener] Local transaction failed", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String txId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("[CartTxListener] Checking local transaction status. TxId: {}", txId);

        CartTransaction tx = cartTransactionMapper.selectById(txId);
        if (tx != null) {
            log.info("[CartTxListener] Check result: COMMIT (tx log exists). TxId: {}", txId);
            return RocketMQLocalTransactionState.COMMIT;
        }

        log.warn("[CartTxListener] Check result: ROLLBACK (tx log missing). TxId: {}", txId);
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
