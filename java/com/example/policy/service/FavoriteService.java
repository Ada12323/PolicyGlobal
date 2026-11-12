package com.example.policy.service;

import com.example.policy.dto.FavoriteDTO;
import com.example.policy.entity.UserFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FavoriteService {

    // 原有方法
    UserFavorite addFavorite(Long userId, Long policyDataId);
    boolean cancelFavorite(Long userId, Long policyDataId);
    boolean cancelFavoriteById(Long favoriteId);
    boolean checkIsFavorite(Long userId, Long policyDataId);
    Page<FavoriteDTO> getFavoritesByPage(Long userId, Pageable pageable);

    // 新增方法（供Controller使用）
    boolean addFavoriteBoolean(Long userId, Long policyDataId);
    boolean checkFavoriteStatus(Long userId, Long policyDataId);
    boolean removeFavorite(Long userId, Long policyDataId);
    boolean cancelFavoriteById(Long favoriteId, Long userId);
}