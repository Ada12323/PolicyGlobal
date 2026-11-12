package com.example.policy.repository;

import com.example.policy.entity.PolicyData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyDataRepository extends JpaRepository<PolicyData, Long> {
    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org o WHERE (:kw IS NULL OR (p.zhTitle LIKE %:kw% OR p.originalTitle LIKE %:kw% OR p.summary LIKE %:kw%)) " +
        "AND (:start IS NULL OR p.publishDate >= :start) AND (:end IS NULL OR p.publishDate <= :end) ORDER BY p.publishDate DESC")
    List<PolicyData> search(@Param("kw") String keyword,
                @Param("start") LocalDate start,
                @Param("end") LocalDate end,
                org.springframework.data.domain.Pageable pageable);

    @Query("SELECT o.orgName, COUNT(p.id) FROM PolicyData p LEFT JOIN p.org o GROUP BY o.orgName")
    List<Object[]> aggregateByOrg();

    @Modifying
    @Transactional
    @Query("UPDATE PolicyData p SET p.clicks = COALESCE(p.clicks, 0) + 1 WHERE p.id = :id")
    void incrementClicks(@Param("id") Long id);

    // 注意：分页+fetch join 在部分实现下会被移除，改为两步法：先查ID再按ID fetch join
    @Query("SELECT p.id FROM PolicyData p ORDER BY p.clicks DESC")
    List<Long> findTopIds(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org o WHERE p.id IN :ids ORDER BY p.clicks DESC")
    List<PolicyData> findByIdInFetch(@Param("ids") List<Long> ids);

    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org o WHERE p.id = :id")
    java.util.Optional<PolicyData> findByIdWithOrg(@Param("id") Long id);

    @Query("SELECT p.id FROM PolicyData p ORDER BY p.publishDate DESC")
    List<Long> findLatestIds(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org o WHERE p.id IN :ids")
    List<PolicyData> findByIdInFetchNoOrder(@Param("ids") List<Long> ids);

    // 随机抽样若干条作为示例参考（MySQL: RAND）
    @Query("SELECT p FROM PolicyData p ORDER BY function('RAND')")
    List<PolicyData> findRandom(org.springframework.data.domain.Pageable pageable);

    // 判重：根据 PDF URL 是否已存在
    boolean existsByPdfUrl(String pdfUrl);

    // 规范化判重：忽略大小写与首尾空格
    @Query("SELECT CASE WHEN COUNT(p.id) > 0 THEN true ELSE false END FROM PolicyData p WHERE LOWER(TRIM(p.pdfUrl)) = LOWER(TRIM(:pdf))")
    boolean existsByPdfUrlNormalized(@Param("pdf") String pdfUrl);

    // 找出重复的规范化 PDF URL 键（出现次数>1）
    @Query("SELECT LOWER(TRIM(p.pdfUrl)) AS key, COUNT(p.id) AS cnt FROM PolicyData p WHERE p.pdfUrl IS NOT NULL AND p.pdfUrl <> '' GROUP BY LOWER(TRIM(p.pdfUrl)) HAVING COUNT(p.id) > 1")
    List<Object[]> findDuplicatePdfUrlKeys();

    // 按规范化 PDF URL 获取所有记录
    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org o WHERE LOWER(TRIM(p.pdfUrl)) = :norm")
    List<PolicyData> findByNormalizedPdf(@Param("norm") String normalizedPdfUrl);

    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org WHERE p.id IN :ids")
    List<PolicyData> findByIdIn(@Param("ids") List<Long> ids);

    // 按PDF URL查询政策（全局唯一）
    Optional<PolicyData> findByPdfUrl(String pdfUrl);
}
