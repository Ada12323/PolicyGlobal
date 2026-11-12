package com.example.policy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FavoritePageController {

    @GetMapping("/favorite")
    public String favoritePage() {
        return "favorite";
    }
}