package com.example.policy.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PdfUrlValidator {

    @Autowired
    private CloseableHttpClient httpClient;

    // 随机User-Agent列表，避免被目标网站屏蔽
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/120.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    public ValidationResult validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return ValidationResult.invalid("URL为空");
        }

        // 检查URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ValidationResult.invalid("URL格式不正确");
        }

        HttpHead request = new HttpHead(url);
        long startTime = System.currentTimeMillis();

        // 设置请求头
        request.setHeader("User-Agent", getRandomUserAgent());
        request.setHeader("Accept", "application/pdf, */*");
        request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        request.setHeader("Connection", "close");

        // 设置超时
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5, TimeUnit.SECONDS)
                .setResponseTimeout(10, TimeUnit.SECONDS)
                .setConnectionRequestTimeout(5, TimeUnit.SECONDS)
                .build();
        request.setConfig(config);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            long responseTime = System.currentTimeMillis() - startTime;
            int statusCode = response.getCode();

            // 消耗响应实体，避免连接泄漏
            EntityUtils.consume(response.getEntity());

            // 判断状态码
            if (statusCode == HttpStatus.SC_OK) {
                return ValidationResult.valid(responseTime);
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return ValidationResult.invalid("404 Not Found", responseTime);
            } else if (statusCode == HttpStatus.SC_FORBIDDEN) {
                return ValidationResult.invalid("403 Forbidden (禁止访问)", responseTime);
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                return ValidationResult.invalid("401 Unauthorized (需要认证)", responseTime);
            } else if (statusCode >= 500) {
                return ValidationResult.invalid("服务器错误: " + statusCode, responseTime);
            } else {
                return ValidationResult.invalid("HTTP " + statusCode, responseTime);
            }

        } catch (IOException e) {
            long responseTime = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();

            if (errorMsg.contains("ConnectTimeoutException") || errorMsg.contains("timed out")) {
                return ValidationResult.invalid("连接超时", responseTime);
            } else if (errorMsg.contains("UnknownHostException")) {
                return ValidationResult.invalid("域名无法解析", responseTime);
            } else if (errorMsg.contains("Connection refused")) {
                return ValidationResult.invalid("连接被拒绝", responseTime);
            } else if (errorMsg.contains("SSLException")) {
                return ValidationResult.invalid("SSL证书错误", responseTime);
            } else {
                return ValidationResult.invalid("网络错误: " + e.getMessage(), responseTime);
            }
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("验证URL异常: {}, URL: {}", e.getMessage(), url, e);
            return ValidationResult.invalid("验证异常: " + e.getMessage(), responseTime);
        }
    }

    private String getRandomUserAgent() {
        Random random = new Random();
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    // 验证结果内部类
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Long responseTime;

        private ValidationResult(boolean valid, String errorMessage, Long responseTime) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.responseTime = responseTime;
        }

        public static ValidationResult valid(Long responseTime) {
            return new ValidationResult(true, null, responseTime);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, 0L);
        }

        public static ValidationResult invalid(String errorMessage, Long responseTime) {
            return new ValidationResult(false, errorMessage, responseTime);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Long getResponseTime() {
            return responseTime;
        }
    }
}