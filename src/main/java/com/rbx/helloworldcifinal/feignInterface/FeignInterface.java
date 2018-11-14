package com.rbx.helloworldcifinal.feignInterface;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "helloService", url = "http://hello-world:8000/")
public interface FeignInterface {
    @RequestMapping(method = RequestMethod.GET, value = "/hello/say")
    String sayHello(@RequestParam(value = "name") String name);
}

