package com.example.policy.crawler;

import com.example.policy.llm.IngestService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 已废弃爬虫逻辑：本类仅作为兼容层转发到 LLM 深度检索服务。
 * 实际的数据采集完全由 Prompt 驱动（RAG + Grok Deep Research），不再进行网页抓取。
 */
@Service
public class SmartCrawler {
    private final IngestService ingestService;
    public SmartCrawler(IngestService ingestService){
        this.ingestService = ingestService;
    }

    public Map<String,Object> crawl(LocalDate start, LocalDate end, int limit){
    return ingestService.ingest(start, end, limit, null);
    }
}
