package com.huskymqplayground.mq;

import org.apache.rocketmq.spring.annotation.ExtRocketMQTemplateConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@ExtRocketMQTemplateConfiguration(group = "cart-tx-group")
public class CartRocketMQTemplate extends RocketMQTemplate {
}
