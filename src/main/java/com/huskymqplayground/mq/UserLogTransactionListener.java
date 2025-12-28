package com.huskymqplayground.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskymqplayground.domain.LocalTransaction;
import com.huskymqplayground.domain.UserLog;
import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mapper.LocalTransactionMapper;
import com.huskymqplayground.service.UserLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 事务消息监听器
 * 负责执行本地事务和回查本地事务状态
 */
@Slf4j
@Component
@RocketMQTransactionListener
@RequiredArgsConstructor
public class UserLogTransactionListener implements RocketMQLocalTransactionListener {

    private final UserLogService userLogService;
    private final LocalTransactionMapper localTransactionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 执行本地事务
     * 触发时机：Producer 发送半消息(Half Message)成功后
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String txId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("[TxListener] Executing local transaction. TxId: {}", txId);

        try {
            // 1. 解析消息
            String json = new String((byte[]) msg.getPayload(), StandardCharsets.UTF_8);
            UserLogDTO userLogDTO = objectMapper.readValue(json, UserLogDTO.class);

            // 2. 模拟不同的事务场景
            if ("rollback".equals(userLogDTO.getUsername())) {
                log.warn("[TxListener] Scenario: Rollback. Local transaction will fail.");
                return RocketMQLocalTransactionState.ROLLBACK;
            }

            // 3. 执行数据库操作 (本地事务)
            UserLog userLog = new UserLog();
            userLog.setUsername(userLogDTO.getUsername());
            userLog.setOperation(userLogDTO.getOperation());
            userLog.setCreateTime(LocalDateTime.now());
            
            // 调用 Service 执行事务 (业务数据 + 事务日志)
            userLogService.saveUserLogWithTxLog(userLog, txId);

            if ("unknown".equals(userLogDTO.getUsername())) {
                log.warn("[TxListener] Scenario: Unknown. DB inserted but returning UNKNOWN to trigger check.");
                return RocketMQLocalTransactionState.UNKNOWN;
            }

            log.info("[TxListener] Local transaction committed. UserLog ID: {}", userLog.getId());
            return RocketMQLocalTransactionState.COMMIT;

        } catch (Exception e) {
            log.error("[TxListener] Local transaction failed", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 检查本地事务状态
     * 触发时机：Broker 长时间未收到 Commit/Rollback 确认（例如 executeLocalTransaction 返回 UNKNOWN 或 应用崩溃）
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String txId = (String) msg.getHeaders().get("rocketmq_TRANSACTION_ID");
        log.info("[TxListener] Checking local transaction status. TxId: {}", txId);

        // 检查事务日志表中是否存在该 TxId
        LocalTransaction tx = localTransactionMapper.selectById(txId);
        
        if (tx != null) {
            log.info("[TxListener] Check result: Transaction was committed (TxLog found).");
            return RocketMQLocalTransactionState.COMMIT;
        } else {
            log.warn("[TxListener] Check result: Transaction was rolled back (TxLog not found).");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
}
