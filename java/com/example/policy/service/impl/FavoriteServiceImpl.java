package com.example.policy.service.impl;

import com.example.policy.dto.FavoriteDTO;
import com.example.policy.entity.PolicyData;
import com.example.policy.entity.User;
import com.example.policy.entity.UserFavorite;
import com.example.policy.repository.FavoriteRepository;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.FavoriteService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final UserRepository userRepository;
    private final PolicyDataRepository policyDataRepository;
    private final FavoriteRepository favoriteRepository;

    @Override
    public UserFavorite addFavorite(Long userId, Long policyDataId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在，userId：" + userId));
        PolicyData policyData = policyDataRepository.findById(policyDataId)
                .orElseThrow(() -> new EntityNotFoundException("政策不存在，policyDataId：" + policyDataId));

        if(favoriteRepository.existsByUser_IdAndPolicyData_Id(userId, policyDataId)) {
            throw new RuntimeException("已收藏，勿重复操作");
        }

        UserFavorite favorite = UserFavorite.builder()
                .user(user)
                .policyData(policyData)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return favoriteRepository.save(favorite);
    }

    @Override
    public boolean cancelFavorite(Long userId, Long policyDataId) {
        try {

            if (!favoriteRepository.existsByUser_IdAndPolicyData_Id(userId, policyDataId)) {
                log.warn("用户[{}]尝试取消未收藏的政策[{}]", userId, policyDataId);
                return false;
            }


            favoriteRepository.deleteByUser_IdAndPolicyData_Id(userId, policyDataId);
            log.info("用户[{}]取消收藏政策[{}]成功", userId, policyDataId);
            return true;
        } catch (Exception e) {
            log.error("取消收藏失败，userId: {}, policyDataId: {}", userId, policyDataId, e);
            return false;
        }
    }

    @Override
    public boolean cancelFavoriteById(Long favoriteId) {
        try {

            if (!favoriteRepository.existsById(favoriteId)) {
                log.warn("尝试取消不存在的收藏记录[{}]", favoriteId);
                return false;
            }

            favoriteRepository.deleteById(favoriteId);
            log.info("取消收藏记录[{}]成功", favoriteId);
            return true;
        } catch (Exception e) {
            log.error("取消收藏失败，favoriteId: {}", favoriteId, e);
            return false;
        }
    }

    @Override
    public boolean checkIsFavorite(Long userId, Long policyDataId) {
        return favoriteRepository.existsByUser_IdAndPolicyData_Id(userId, policyDataId);
    }

    @Override
    public Page<FavoriteDTO> getFavoritesByPage(Long userId, Pageable pageable) {
        log.info("查询用户[{}]的收藏，分页参数：{}", userId, pageable);
        return favoriteRepository.findFavoriteDTOsByUserId(userId, pageable);
    }


    public boolean addFavoriteBoolean(Long userId, Long policyDataId) {
        try {
            addFavorite(userId, policyDataId);
            return true;
        } catch (Exception e) {
            log.error("添加收藏失败", e);
            return false;
        }
    }


    public boolean checkFavoriteStatus(Long userId, Long policyDataId) {
        try {
            return checkIsFavorite(userId, policyDataId);
        } catch (Exception e) {
            log.error("检查收藏状态失败", e);
            return false;
        }
    }


    public boolean removeFavorite(Long userId, Long policyDataId) {
        try {
            return cancelFavorite(userId, policyDataId);
        } catch (Exception e) {
            log.error("取消收藏失败", e);
            return false;
        }
    }

    public boolean cancelFavoriteById(Long favoriteId, Long userId) {
        try {

            Optional<UserFavorite> favoriteOpt = favoriteRepository.findById(favoriteId);
            if (favoriteOpt.isPresent() && favoriteOpt.get().getUser().getId().equals(userId)) {
                favoriteRepository.deleteById(favoriteId);
                log.info("用户[{}]取消收藏记录[{}]成功", userId, favoriteId);
                return true;
            }
            log.warn("用户[{}]尝试取消不属于自己的收藏记录[{}]", userId, favoriteId);
            return false;
        } catch (Exception e) {
            log.error("根据ID取消收藏失败，favoriteId: {}, userId: {}", favoriteId, userId, e);
            return false;
        }
    }
}