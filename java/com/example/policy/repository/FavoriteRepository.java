package com.example.policy.repository;

import com.example.policy.dto.FavoriteDTO;
import com.example.policy.entity.UserFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<UserFavorite, Long> {

    @Query("SELECT new com.example.policy.dto.FavoriteDTO(" +
            "uf.id, pd.id, pd.zhTitle, pd.summary, uf.createdAt) " +
            "FROM UserFavorite uf " +
            "JOIN uf.policyData pd " +
            "WHERE uf.user.id = :userId")
    Page<FavoriteDTO> findFavoriteDTOsByUserId(@Param("userId") Long userId, Pageable pageable);

    boolean existsByUser_IdAndPolicyData_Id(Long userId, Long policyDataId);

    List<UserFavorite> deleteByUser_IdAndPolicyData_Id(Long userId, Long policyDataId);

    void deleteById(Long favoriteId);

    List<UserFavorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT uf FROM UserFavorite uf JOIN uf.policyData p WHERE uf.user.id = :userId AND p.org.id IN :orgIds ORDER BY uf.createdAt DESC")
    List<UserFavorite> findByUserIdAndPolicyDataOrgIdIn(@Param("userId") Long userId, @Param("orgIds") List<Long> orgIds);


}