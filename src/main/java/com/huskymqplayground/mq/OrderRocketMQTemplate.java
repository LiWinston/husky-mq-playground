package com.huskymqplayground.mq;

import org.apache.rocketmq.spring.annotation.ExtRocketMQTemplateConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@ExtRocketMQTemplateConfiguration(group = "order-tx-group")
public class OrderRocketMQTemplate extends RocketMQTemplate {
}
