package com.example.gate0;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.gate0.mapper")
public class Gate0Application {

    public static void main(String[] args) {
        SpringApplication.run(Gate0Application.class, args);
    }
}
