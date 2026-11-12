package com.example.policy.service;

import java.util.Map;
import java.util.Optional;

public interface RatingService {
    Optional<String> searchRating(Long pid);
    Long addRating(Long pid, Double rating);
    Map<String,Object>avgRating(Long pid);


}
