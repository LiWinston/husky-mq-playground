package com.huskymqplayground.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cart_transaction")
public class CartTransaction {
    @TableId
    private String txId;
    private String username;
    private LocalDateTime createTime;
}
