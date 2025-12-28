package com.huskymqplayground.service;

import com.huskymqplayground.domain.LocalTransaction;
import com.huskymqplayground.domain.UserLog;
import com.huskymqplayground.mapper.LocalTransactionMapper;
import com.huskymqplayground.mapper.UserLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserLogService {

    private final UserLogMapper userLogMapper;
    private final LocalTransactionMapper localTransactionMapper;

    /**
     * 执行业务逻辑并记录事务日志
     * 必须在同一个本地事务中完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserLogWithTxLog(UserLog userLog, String txId) {
        // 1. 业务操作
        userLogMapper.insert(userLog);

        // 2. 记录事务日志 (用于回查)
        LocalTransaction tx = new LocalTransaction();
        tx.setTxId(txId);
        tx.setCreateTime(LocalDateTime.now());
        localTransactionMapper.insert(tx);
    }
}
