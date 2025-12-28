package com.huskymqplayground.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RocketMQIdempotent {
    /**
     * 幂等 Key 的前缀
     */
    String prefix() default "mq:idempotent:";

    /**
     * 锁/Key 的过期时间
     */
    long expire() default 24;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.HOURS;
}
