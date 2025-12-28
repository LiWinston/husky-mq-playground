package com.huskymqplayground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDTO implements Serializable {
    /**
     * 业务唯一标识，用于幂等校验和链路追踪
     */
    private String traceId;
    private String orderNo;
    private String buyer;
    private String itemName;
    private Integer quantity;
    private BigDecimal amount;
}
