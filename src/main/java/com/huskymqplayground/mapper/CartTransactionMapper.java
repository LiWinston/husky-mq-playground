package com.huskymqplayground.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huskymqplayground.domain.CartTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartTransactionMapper extends BaseMapper<CartTransaction> {
}
