package com.huskymqplayground.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartDTO implements Serializable {
    private String traceId;
    private String username;
    private String itemName;
    private Integer quantity;
}
