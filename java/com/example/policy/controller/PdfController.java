package com.example.policy.controller;

import com.example.policy.entity.PolicyData;
import com.example.policy.service.PolicyDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private final PolicyDataService policyDataService;
    private static final Logger log = LoggerFactory.getLogger(PdfController.class);

    public PdfController(PolicyDataService policyDataService) {
        this.policyDataService = policyDataService;
    }

    // 内嵌查看（前端可用 pdf.js 加载 /api/pdf/raw/{id}）
    @GetMapping("/raw/{id}")
    public ResponseEntity<StreamingResponseBody> raw(@PathVariable("id") Long id) {
        PolicyData p = policyDataService.getDetail(id, false);
        if (p == null || p.getPdfUrl() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            URL url = new URL(p.getPdfUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (p.getSourceUrl() != null && !p.getSourceUrl().isBlank()) {
                conn.setRequestProperty("Referer", p.getSourceUrl());
            }
            conn.setInstanceFollowRedirects(true);
            log.debug("Proxying PDF inline, id={}, url={}", id, url);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                InputStream is = conn.getInputStream();
                HttpHeaders headers = new HttpHeaders();
                String fileName = buildPdfFileName(p, id);
                String cd = buildContentDisposition("inline", fileName);
                String ct = conn.getContentType();
                if (ct == null || !ct.toLowerCase().contains("pdf")) {
                    headers.setContentType(MediaType.APPLICATION_PDF);
                } else {
                    headers.set(HttpHeaders.CONTENT_TYPE, ct);
                }
                long len = conn.getContentLengthLong();
                if (len >= 0) headers.setContentLength(len);
                headers.set(HttpHeaders.CONTENT_DISPOSITION, cd);
                StreamingResponseBody body = outputStream -> safeCopyStream(is, outputStream);
                return new ResponseEntity<>(body, headers, HttpStatus.OK);
            } else {
                String tmpMsg;
                try {
                    InputStream es = conn.getErrorStream();
                    if (es != null) {
                        byte[] bytes = es.readAllBytes();
                        tmpMsg = new String(bytes);
                    } else {
                        tmpMsg = "Upstream status=" + code + ", message=" + conn.getResponseMessage();
                    }
                } catch (IOException ioe) {
                    tmpMsg = "Upstream status=" + code + ", message=" + conn.getResponseMessage();
                }
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.TEXT_PLAIN);
                final byte[] body = tmpMsg.getBytes();
                return new ResponseEntity<>(outputStream -> outputStream.write(body), headers, HttpStatus.BAD_GATEWAY);
            }
        } catch (Exception e) {
            log.warn("Proxy inline failed for id={}, msg={}", id, e.toString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    // 下载（复用 raw 输出加 attachment）
    @GetMapping("/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("id") Long id) {
        PolicyData p = policyDataService.getDetail(id, false);
        if (p == null || p.getPdfUrl() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            URL url = new URL(p.getPdfUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            if (p.getSourceUrl() != null && !p.getSourceUrl().isBlank()) {
                conn.setRequestProperty("Referer", p.getSourceUrl());
            }
            conn.setInstanceFollowRedirects(true);
            log.debug("Proxying PDF download, id={}, url={}", id, url);
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                InputStream is = conn.getInputStream();
                HttpHeaders headers = new HttpHeaders();
                String fileName = buildPdfFileName(p, id);
                String cd = buildContentDisposition("attachment", fileName);
                String ct = conn.getContentType();
                if (ct == null || !ct.toLowerCase().contains("pdf")) {
                    headers.setContentType(MediaType.APPLICATION_PDF);
                } else {
                    headers.set(HttpHeaders.CONTENT_TYPE, ct);
                }
                long len = conn.getContentLengthLong();
                if (len >= 0) headers.setContentLength(len);
                headers.set(HttpHeaders.CONTENT_DISPOSITION, cd);
                StreamingResponseBody body = outputStream -> safeCopyStream(is, outputStream);
                return new ResponseEntity<>(body, headers, HttpStatus.OK);
            } else {
                String tmpMsg;
                try {
                    InputStream es = conn.getErrorStream();
                    if (es != null) {
                        byte[] bytes = es.readAllBytes();
                        tmpMsg = new String(bytes);
                    } else {
                        tmpMsg = "Upstream status=" + code + ", message=" + conn.getResponseMessage();
                    }
                } catch (IOException ioe) {
                    tmpMsg = "Upstream status=" + code + ", message=" + conn.getResponseMessage();
                }
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.TEXT_PLAIN);
                final byte[] body = tmpMsg.getBytes();
                return new ResponseEntity<>(outputStream -> outputStream.write(body), headers, HttpStatus.BAD_GATEWAY);
            }
        } catch (Exception e) {
            log.warn("Proxy download failed for id={}, msg={}", id, e.toString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    // 更稳健的复制：吞掉流关闭/客户端中止等异常，避免 500/Whitelabel
    private void safeCopyStream(InputStream in, OutputStream out) {
        byte[] buffer = new byte[8192];
        InputStream is = in;
        try {
            int len;
            while ((len = is.read(buffer)) != -1) {
                try {
                    out.write(buffer, 0, len);
                } catch (IOException clientAbort) {
                    // 客户端中止（标签关闭等），结束即可
                    break;
                }
            }
            try { out.flush(); } catch (IOException ignore) {}
        } catch (IOException readErr) {
            // 上游读取异常：结束即可
        } finally {
            try { if (is != null) is.close(); } catch (IOException ignore) {}
        }
    }

    private String buildPdfFileName(com.example.policy.entity.PolicyData p, Long id){
        String org = null;
        try { org = (p.getOrg() != null ? p.getOrg().getOrgName() : null); } catch (Exception ignored) {}
        String title = p.getZhTitle();
        String base = ((org!=null && !org.isBlank())? (org + "-") : "") + (title!=null && !title.isBlank()? title : ("policy-"+id));
        base = sanitizeFileName(base);
        if (!base.toLowerCase().endsWith(".pdf")) base += ".pdf";
        return base;
    }

    private String sanitizeFileName(String name){
        // 去除非法字符，收敛空白和长度
        String v = name.replaceAll("[\\\\/:*?\"<>|]", " ")
                       .replaceAll("\r|\n", " ")
                       .replaceAll("\s+", " ")
                       .trim();
        if (v.length() > 160) v = v.substring(0, 160).trim();
        return v.isEmpty() ? "policy" : v;
    }

    private String buildContentDisposition(String type, String filename){
        // RFC 5987: filename* with UTF-8 percent-encoding + ASCII 退化名
        String ascii = filename.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}