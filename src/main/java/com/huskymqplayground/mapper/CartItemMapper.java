package com.huskymqplayground.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huskymqplayground.domain.CartItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
