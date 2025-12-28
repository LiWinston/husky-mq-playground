package com.huskymqplayground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLogDTO implements Serializable {
    /**
     * 业务唯一标识，用于幂等校验和链路追踪
     */
    private String traceId;
    private String username;
    private String operation;
}
