package com.huskymqplayground.controller;

import com.huskymqplayground.dto.UserLogDTO;
import com.huskymqplayground.mq.AsyncSaveProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final AsyncSaveProducer asyncSaveProducer;

    @PostMapping("/log")
    public String sendLog(@RequestBody UserLogDTO userLogDTO) {
        asyncSaveProducer.sendUserLog(userLogDTO);
        return "Message sent successfully";
    }
}
