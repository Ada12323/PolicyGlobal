package com.example.policy.service.impl;

import com.example.policy.entity.Rating;
import com.example.policy.repository.RatingRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.RatingService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class RatingServiceImpl implements RatingService {
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;

    public RatingServiceImpl(RatingRepository ratingRepository, UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> searchRating(Long pid) {
        Long uid;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // 从数据库查询用户信息，提取 ID（若不存在则返回 null）
        uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        return ratingRepository.findByUserIdAndPolicyId(uid, pid);
    }

    @Override
    public Long addRating(Long pid, Double rating) {
        Long uid;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // 从数据库查询用户信息，提取 ID（若不存在则返回 null）
        uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        Rating r = new Rating();
        r.setUserId(uid);
        r.setPolicyId(pid);
        r.setRating(rating);
        return ratingRepository.save(r).getId();


    }

    @Override
    public Map<String, Object> avgRating(Long pid) {
        Double avg = ratingRepository.findAverageRatingByPolicyId(pid).orElse(0.0);
        Long count = ratingRepository.findCountRatingByPolicyId(pid).orElse(0L);
        Map<String, Object> map = new HashMap<>();
        map.put("avg", avg);
        map.put("count", count);
        return map;
    }
}
