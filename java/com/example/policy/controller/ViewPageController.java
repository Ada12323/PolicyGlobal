package com.example.policy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewPageController {

    @GetMapping({"/", ""})
    public String home(){
        return "home";
    }

    @GetMapping("/login")
    public String login(){
        return "login";
    }

    @GetMapping("/policy/{id}")
    public String detail(){
        return "policy-detail";
    }

    @GetMapping("/wordcloud")
    public String wordcloud(){
        return "wordcloud";
    }

    @GetMapping("/admin")
    public String admin(){
        return "admin";
    }

    @GetMapping("/agent")
    public String agent(){
        return "agent";
    }

    @GetMapping("/allpolicy")
    public String allpolicy(){
        return "allpolicy";
    }

    @GetMapping("/change_password")
    public String change_password(){ return "change_password"; }

    @GetMapping("/admin_pdf_validation")
    public String admin_pdf_validation(){ return "admin-pdf-validation"; }

    @GetMapping("/mysearch")
    public String mysearch(){ return "mysearch"; }

    @GetMapping("/my-policy-detail/{id}")
    public String myPolicyDetail(){
        return "my-policy-detail";
    }

}
