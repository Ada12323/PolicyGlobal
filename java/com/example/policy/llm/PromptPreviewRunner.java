package com.example.policy.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(prefix = "prompt.preview", name = "enabled", havingValue = "true")
public class PromptPreviewRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(PromptPreviewRunner.class);

	private final IngestService ingestService;

	@Value("${prompt.preview.start-date:}")
	private String startDateProperty;

	@Value("${prompt.preview.end-date:}")
	private String endDateProperty;

	@Value("${prompt.preview.limit:20}")
	private int limit;

	@Value("${prompt.preview.org-ids:}")
	private String orgIdsProperty;

	public PromptPreviewRunner(IngestService ingestService) {
		this.ingestService = ingestService;
	}

	@Override
	public void run(String... args) {
		LocalDate now = LocalDate.now();
		LocalDate start = parseDateOrDefault(startDateProperty, now.minusDays(30));
		LocalDate end = parseDateOrDefault(endDateProperty, now);
		if (end.isBefore(start)) {
			log.warn("End date {} is before start date {}, swapping.", end, start);
			LocalDate tmp = start;
			start = end;
			end = tmp;
		}

		List<Long> orgIds = parseOrgIds(orgIdsProperty);

		log.info("[Prompt Preview] start={}, end={}, limit={}, orgIds={}", start, end, limit, orgIds);
		String prompt = ingestService.buildPrompt(start, end, limit, orgIds.isEmpty() ? null : orgIds);
		log.info("\n================= Prompt Preview =================\n{}\n==================================================\n", prompt);

		// Terminate application after preview to avoid keeping the web server running
		Thread shutdownHook = new Thread(() -> {
			log.debug("Triggering graceful shutdown after prompt preview.");
			System.exit(0);
		});
		shutdownHook.setDaemon(false);
		shutdownHook.start();
	}

	private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_DATE);
		} catch (DateTimeParseException e) {
			log.warn("Unable to parse date '{}', using fallback {}", value, fallback);
			return fallback;
		}
	}

	private List<Long> parseOrgIds(String raw) {
		if (raw == null || raw.isBlank()) {
			return Collections.emptyList();
		}
		return Stream.of(raw.split(","))
			.map(String::trim)
			.filter(token -> !token.isEmpty())
			.flatMap(token -> {
				try {
					return Stream.of(Long.parseLong(token));
				} catch (NumberFormatException ex) {
					log.warn("Skipping invalid org id token: {}", token);
					return Stream.empty();
				}
			})
			.collect(Collectors.toList());
	}
}
