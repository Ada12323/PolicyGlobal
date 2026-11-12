package com.example.policy.repository;

import com.example.policy.entity.PdfValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PdfValidationRepository extends JpaRepository<PdfValidation, Long> {

    Optional<PdfValidation> findByPolicyId(Long policyId);

    List<PdfValidation> findByStatus(Integer status);

    @Modifying
    @Transactional
    @Query("DELETE FROM PdfValidation pv WHERE pv.policyId IN :policyIds")
    void deleteByPolicyIds(@Param("policyIds") List<Long> policyIds);

    @Query("SELECT pv FROM PdfValidation pv WHERE pv.status = 2 ORDER BY pv.lastCheckTime DESC")
    List<PdfValidation> findAllInvalid();

    @Query("SELECT COUNT(pv) FROM PdfValidation pv WHERE pv.status = :status")
    long countByStatus(@Param("status") Integer status);
}