package com.example.policy.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 静态资源与登录页放行
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/login").permitAll()

                        // 页面（HTML 模板）需登录访问
                        .requestMatchers("/", "/wordcloud", "/policy/*", "/agent").authenticated()

                        // 政策助手浮动聊天球：允许未登录访问
                        .requestMatchers("/api/assistant/**").permitAll()

                        .requestMatchers("/api/agent/probe/**").authenticated()
                        .requestMatchers("/api/agent/prompt", "/api/agent/prompt/runtime").authenticated()
                        .requestMatchers("/api/agent/diagnose", "/api/agent/diagnose/detailed").authenticated()
                        .requestMatchers("/api/agent/orgs").authenticated()

                        // 仅管理员可以访问的敏感操作"/admin"
                        .requestMatchers("/api/agent/crawl").hasRole("ADMIN")
                        .requestMatchers("/api/agent/orgs").hasRole("ADMIN") // POST 创建机构需要管理员

                        // 管理与敏感能力仅管理员
                        .requestMatchers("/api/policy/ingest/**", "/api/admin/**").hasRole("ADMIN")

                        // API 鉴权白名单（登录/注册/健康检查/会话探测/登出）
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/session", "/api/auth/logout", "/api/health").permitAll()

                        // 其余 API 需登录
                        .requestMatchers("/api/**").authenticated()

                        // 其它（如静态页）默认放行
                        .anyRequest().permitAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            String uri = req.getRequestURI();
                            String accept = req.getHeader("Accept");
                            boolean isApi = uri != null && uri.startsWith("/api/");
                            boolean wantsHtml = accept != null && accept.contains("text/html");
                            if (!isApi && wantsHtml) {
                                // 未登录访问页面：重定向到登录
                                res.sendRedirect("/login");
                                return;
                            }
                            // API 或非 HTML 请求：返回 JSON 401
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"code\":403,\"message\":\"Forbidden\"}");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

