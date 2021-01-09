package com.yyl.loginservice.controller;

import com.yyl.loginservice.bean.User;
import com.yyl.loginservice.service.UserLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class TestController {


    @Autowired
    UserLoginService userLoginService;

    @PostMapping("/register")
    public void register(User user){

        userLoginService.register(user);

        System.out.println("controller:"+user);

    }

    @GetMapping("/")
    public String index(){
        return "/index";
    }


}
