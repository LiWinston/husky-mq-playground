package com.huskymqplayground.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_log")
public class UserLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String operation;
    private LocalDateTime createTime;
}
