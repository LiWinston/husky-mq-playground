package com.huskymqplayground.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huskymqplayground.domain.OrderTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderTransactionMapper extends BaseMapper<OrderTransaction> {
}
