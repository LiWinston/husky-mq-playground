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
        
        // Send async message
        rocketMQTemplate.asyncSend(topic, MessageBuilder.withPayload(userLogDTO).build(), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("Send message success. msgId: {}", sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("Send message failed.", e);
            }
        });
    }
}
