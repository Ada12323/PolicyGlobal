package com.example.policy.controller;

import com.example.policy.dto.AuthResponse;
import com.example.policy.dto.LoginRequest;
import com.example.policy.dto.RegisterRequest;
import com.example.policy.entity.Role;
import com.example.policy.entity.User;
import com.example.policy.repository.RoleRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.security.JwtUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        Role userRole = roleRepository.findByCode("USER").orElseThrow(() -> new RuntimeException("角色缺失"));
        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .roles(Collections.singleton(userRole))
                .build();
        userRepository.save(user);
        String token = jwtUtil.generateToken(user.getUsername());
        setAuthCookie(response, token);
        return new AuthResponse(token);
    }


    @GetMapping("/users/Id")
    public Long getMyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User u = userRepository.findByUsername(username).orElseThrow();
        return u.getId();
    }

    @GetMapping("/users/Name")
    public String getMyUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    @PutMapping("/password/change")
    @Transactional
    public ResponseEntity<?> updatePassword(@RequestBody Map<String,Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User u = userRepository.findByUsername(username).orElseThrow();
        String password = (String) body.get("password");
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("密码不能为空");
        }
        u.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("账号或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new RuntimeException("账户已被禁用");
        }
    String token = jwtUtil.generateToken(user.getUsername());
    setAuthCookie(response, token);
    return new AuthResponse(token);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        // 需要已登录；未登录将被 Security 的 EntryPoint 以 401 JSON 返回
    String username = authentication != null ? authentication.getName() : null;
    List<String> roles = authentication != null
        ? authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
        : List.of();
    return Map.of(
        "username", username,
        "roles", roles
    );
    }

    // 轻量会话探测：未登录也返回 200，前端用它来判断是否需要跳转登录
    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication) {
        Authentication auth = authentication;
        if (auth == null || !auth.isAuthenticated()) {
            return Map.of("authenticated", false);
        }
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return Map.of(
                "authenticated", true,
                "username", auth.getName(),
                "roles", roles
        );
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        // 清除 HttpOnly Cookie
        String cookie = "jwt=; Max-Age=0; Path=/; SameSite=Lax"; // 非 https 环境不加 Secure
        response.addHeader("Set-Cookie", cookie);
        return Map.of("ok", true);
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        // 在 http 环境下不要加 Secure；生产 https 环境建议加上 Secure
        long maxAge = 60L * 60L * 2L; // 2 小时，和默认过期时间一致
        String cookie = String.format("jwt=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax", token, maxAge);
        response.addHeader("Set-Cookie", cookie);
    }
}
