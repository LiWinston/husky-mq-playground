package com.huskymqplayground.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * V3 基础监听器：封装 MessageExt 到 DTO 的反序列化逻辑
 * 同时默认配置了 CONSUME_FROM_LAST_OFFSET 策略
 * @param <T> DTO 类型
 */
@Slf4j
public abstract class BaseRocketMQListener<T> implements RocketMQListener<MessageExt>, RocketMQPushConsumerLifecycleListener {

    @Autowired
    protected ObjectMapper objectMapper;

    private final Class<T> messageType;

    @SuppressWarnings("unchecked")
    public BaseRocketMQListener() {
        // 通过反射获取泛型 T 的具体类型
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            this.messageType = (Class<T>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
        } else {
            this.messageType = (Class<T>) Object.class;
        }
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        // 默认策略：只消费启动后的新消息（针对新组）
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
    }

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            // 1. 自动反序列化
            String json = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            T dto = objectMapper.readValue(json, messageType);
            
            // 2. 调用业务处理方法
            handleMessage(dto, messageExt);
            
        } catch (Exception e) {
            log.error("Message deserialization or processing failed. MsgId: {}", messageExt.getMsgId(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 业务处理方法
     * @param dto 反序列化后的 DTO 对象
     * @param messageExt 原始消息（包含 Keys, Tags 等元数据）
     */
    protected abstract void handleMessage(T dto, MessageExt messageExt);
}
