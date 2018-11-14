package com.rbx.helloworldcifinal.controller;

import com.rbx.helloworldcifinal.feignInterface.FeignInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("")
public class HelloController {

    @Autowired
    private FeignInterface feignInterface;

    @GetMapping("getHello")
    public String getHello(@RequestParam(value = "name", defaultValue = "nobody") String name) {
        return feignInterface.sayHello(name);
    }
}
