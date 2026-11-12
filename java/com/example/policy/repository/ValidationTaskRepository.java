package com.example.policy.repository;

import com.example.policy.entity.ValidationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ValidationTaskRepository extends JpaRepository<ValidationTask, Long> {

    Optional<ValidationTask> findByStatusIn(List<String> statuses);

    @Query("SELECT t FROM ValidationTask t WHERE t.status = 'RUNNING'")
    Optional<ValidationTask> findRunningTask();

    @Modifying
    @Transactional
    @Query("UPDATE ValidationTask t SET t.processedCount = :processed WHERE t.id = :id")
    void updateProcessedCount(@Param("id") Long id, @Param("processed") Integer processed);

    @Modifying
    @Transactional
    @Query("UPDATE ValidationTask t SET t.validCount = :valid WHERE t.id = :id")
    void updateValidCount(@Param("id") Long id, @Param("valid") Integer valid);

    @Modifying
    @Transactional
    @Query("UPDATE ValidationTask t SET t.invalidCount = :invalid WHERE t.id = :id")
    void updateInvalidCount(@Param("id") Long id, @Param("invalid") Integer invalid);

    @Modifying
    @Transactional
    @Query("UPDATE ValidationTask t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    @Query("SELECT t FROM ValidationTask t ORDER BY t.createdAt DESC")
    List<ValidationTask> findAllOrderByCreatedAtDesc();
}