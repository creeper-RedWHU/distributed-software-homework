package com.example.demo.controller;


import com.example.demo.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ping")
public class PingPong {
    @GetMapping("/")
    public Result<String>PingPongJudge(){
        return Result.success();
    }
}
