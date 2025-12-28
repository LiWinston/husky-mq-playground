package com.huskymqplayground.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huskymqplayground.domain.LocalTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocalTransactionMapper extends BaseMapper<LocalTransaction> {
}
