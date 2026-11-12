package com.example.policy.controller;

import com.example.policy.service.WordCloudService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wordcloud")
public class WordCloudController {

    private final WordCloudService wordCloudService;

    public WordCloudController(WordCloudService wordCloudService) {
        this.wordCloudService = wordCloudService;
    }

    @GetMapping("/top")
    public List<Map<String, Object>> top(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        return wordCloudService.topWords(limit);
    }
}
