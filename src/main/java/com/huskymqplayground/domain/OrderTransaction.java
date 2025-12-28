package com.huskymqplayground.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_transaction")
public class OrderTransaction {
    @TableId
    private String txId;
    private String orderNo;
    private LocalDateTime createTime;
}
