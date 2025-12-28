package com.huskymqplayground;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.huskymqplayground.mapper")
public class HuskyMqPlaygroundApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuskyMqPlaygroundApplication.class, args);
    }

}
