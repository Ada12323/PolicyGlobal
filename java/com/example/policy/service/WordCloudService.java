package com.example.policy.service;

import java.util.List;
import java.util.Map;

public interface WordCloudService {
    List<Map<String, Object>> topWords(int limit);
}
