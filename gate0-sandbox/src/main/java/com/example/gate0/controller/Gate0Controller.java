package com.example.gate0.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Gate 0 冒烟")
@RestController
@RequestMapping("/gate0")
public class Gate0Controller {

    @Operation(summary = "存活探针")
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "UP", "stack", "Boot 4.1.1 + JDK 21");
    }

    @Operation(summary = "回显路径变量")
    @GetMapping("/echo/{value}")
    public Map<String, Object> echo(@PathVariable String value) {
        return Map.of("echo", value);
    }
}
