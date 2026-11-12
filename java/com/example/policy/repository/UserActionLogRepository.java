package com.example.policy.repository;

import com.example.policy.entity.UserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {
	List<UserActionLog> findByUserIdAndActionTypeOrderByActionTimeDesc(Long userId, String actionType, Pageable pageable);
}
