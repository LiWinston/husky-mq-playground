package com.huskymqplayground.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("local_transaction")
public class LocalTransaction {
    @TableId
    private String txId;
    private LocalDateTime createTime;
}
