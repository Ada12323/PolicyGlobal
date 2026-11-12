package com.example.policy.llm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class AutoIngestScheduler {

    @Value("${llm.auto.enabled:true}")
    private boolean enabled;

    private final IngestService ingestService;

    public AutoIngestScheduler(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    // 默认每月1日02:00运行
    @Scheduled(cron = "${llm.auto.cron:0 0 2 1 * ?}")
    public void runMonthlyIngest() {
        if (!enabled) return;
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        LocalDate start = lastMonth.atDay(1);
        LocalDate end = lastMonth.atEndOfMonth();
    var res = ingestService.ingest(start, end, 50, null);
        System.out.println("[LLM INGEST] 采集区间: " + start + " ~ " + end + " => " + res);
    }
}
