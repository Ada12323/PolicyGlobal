package com.example.policy.logging;

import com.example.policy.entity.UserActionLog;
import com.example.policy.repository.UserActionLogRepository;
import com.example.policy.repository.UserRepository;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class UserActionLogger {

    private final UserActionLogRepository userActionLogRepository;
    private final UserRepository userRepository;

    public UserActionLogger(UserActionLogRepository userActionLogRepository, UserRepository userRepository) {
        this.userActionLogRepository = userActionLogRepository;
        this.userRepository = userRepository;
    }

    @AfterReturning(value = "execution(* com.example.policy.controller.PolicyController.detail(..)) && args(id)")
    public void logDetail(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Long uid = null;
            try {
                String username = auth.getName();
                uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
            } catch (Exception ignored) {}
            UserActionLog log = UserActionLog.builder()
                    .userId(uid)
                    .policyId(id)
                    .actionType("VIEW_DETAIL")
                    .build();
            userActionLogRepository.save(log);
        }
    }
}
