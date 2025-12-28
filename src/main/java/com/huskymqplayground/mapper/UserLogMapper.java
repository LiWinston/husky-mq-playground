package com.huskymqplayground.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huskymqplayground.domain.UserLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLogMapper extends BaseMapper<UserLog> {
}
