package com.huskymqplayground.controller;

import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mq.AsyncSaveProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final AsyncSaveProducer asyncSaveProducer;

    @PostMapping("/log")
    public String sendLog(@RequestBody UserLogDTO userLogDTO) {
        // 生成 TraceId (业务 Key)
        String traceId = UUID.randomUUID().toString();
        userLogDTO.setTraceId(traceId);
        
        asyncSaveProducer.sendUserLog(userLogDTO);
        return "Message sent successfully. TraceId: " + traceId;
    }

    @PostMapping("/ordered-log")
    public String sendOrderedLog(@RequestBody UserLogDTO baseUserLog) {
        String username = baseUserLog.getUsername();
        if (username == null || username.isEmpty()) {
            return "Username is required for ordered test (used as sharding key)";
        }

        StringBuilder sb = new StringBuilder();
        // 发送 8 条顺序消息
        for (int i = 1; i <= 8; i++) {
            UserLogDTO dto = new UserLogDTO();
            dto.setUsername(username);
            dto.setOperation(baseUserLog.getOperation() + "_Step" + i);
            dto.setTraceId(UUID.randomUUID().toString());
            
            // 使用 username 作为 hashKey，确保进入同一个 Queue
            asyncSaveProducer.sendOrderedUserLog(dto, username);
            sb.append("Step").append(i).append(" sent (TraceId: ").append(dto.getTraceId()).append(")\n");
        }
        
        return "Ordered messages sent:\n" + sb.toString();
    }

    @PostMapping("/transactional-log")
    public String sendTransactionalLog(@RequestBody UserLogDTO userLogDTO) {
        String traceId = UUID.randomUUID().toString();
        userLogDTO.setTraceId(traceId);
        
        // 发送事务消息
        // 注意：这里的 "发送" 只是发送 Half Message，真正的业务逻辑(DB插入)在 Listener 中执行
        asyncSaveProducer.sendTransactionalUserLog(userLogDTO);
        
        return "Transactional message sent (Half Message). TraceId: " + traceId + 
               ". Check logs for 'TxListener' to see local transaction execution.";
    }
}
