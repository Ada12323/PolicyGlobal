package com.example.policy.controller;


import com.example.policy.service.RatingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/policy","my-policy-detail"})
public class RatingController {
    private final RatingService ratingService;
    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping("/api/searchrating")
    public Optional<String> sraechrating(Long pid)  {
       return ratingService.searchRating(pid);
    }

    @PostMapping("/api/addrating")
    public Long addRating(Long pid,Double rating) {
        return ratingService.addRating(pid,rating);
    }

    @GetMapping("/api/avgrating")
    public Map<String,Object> avgRating(Long pid) {
        return ratingService.avgRating(pid);
    }



}
