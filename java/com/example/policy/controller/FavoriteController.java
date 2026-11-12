package com.example.policy.controller;

import com.example.policy.dto.ApiResponse;
import com.example.policy.dto.CheckFavoriteResponse;
import com.example.policy.dto.FavoriteDTO;
import com.example.policy.dto.FavoriteRequest;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(FavoriteController.class);

    @PostMapping
    public ResponseEntity<ApiResponse> addFavorite(@RequestBody FavoriteRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userRepository.findByUsername(username)
                    .map(user -> user.getId())
                    .orElseThrow(() -> new RuntimeException("用户未登录或不存在"));

            boolean success = favoriteService.addFavoriteBoolean(userId, request.getPolicyDataId());
            if (success) {
                return ResponseEntity.ok(new ApiResponse(true, "收藏成功", null));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "收藏失败", null));
            }
        } catch (Exception e) {
            logger.error("添加收藏失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, "添加收藏失败: " + e.getMessage(), null));
        }
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse> checkFavorite(@RequestParam Long policyDataId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userRepository.findByUsername(username)
                    .map(user -> user.getId())
                    .orElseThrow(() -> new RuntimeException("用户未登录或不存在"));

            boolean isFavorited = favoriteService.checkFavoriteStatus(userId, policyDataId);
            CheckFavoriteResponse response = new CheckFavoriteResponse(isFavorited);
            return ResponseEntity.ok(new ApiResponse(true, "查询成功", response));
        } catch (Exception e) {
            logger.error("检查收藏状态失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, "检查收藏状态失败: " + e.getMessage(), null));
        }
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse> removeFavoriteByPolicy(@RequestParam Long policyDataId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userRepository.findByUsername(username)
                    .map(user -> user.getId())
                    .orElseThrow(() -> new RuntimeException("用户未登录或不存在"));

            boolean success = favoriteService.removeFavorite(userId, policyDataId);
            if (success) {
                return ResponseEntity.ok(new ApiResponse(true, "取消收藏成功", null));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "取消收藏失败", null));
            }
        } catch (Exception e) {
            logger.error("取消收藏失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, "取消收藏失败: " + e.getMessage(), null));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String keyword) {

        try {
            if (sort.contains("createTime")) {
                sort = sort.replace("createTime", "createdAt");
                logger.warn("检测到 createTime 排序参数，已转换为 createdAt");
            }

            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1])
                    ? Sort.Direction.ASC : Sort.Direction.DESC;

            String sortField = sortParams[0];
            Sort pageSort = Sort.by(direction, sortField);
            Pageable pageable = PageRequest.of(page, size, pageSort);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);

            if (uid == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "用户未登录或不存在", null));
            }

            Page<FavoriteDTO> favorites = favoriteService.getFavoritesByPage(uid, pageable);
            return ResponseEntity.ok(new ApiResponse(true, "查询成功", favorites));

        } catch (Exception e) {
            logger.error("获取收藏列表失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, "获取收藏列表失败: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{favoriteId}")
    public ResponseEntity<ApiResponse> removeFavorite(@PathVariable Long favoriteId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            Long userId = userRepository.findByUsername(username)
                    .map(user -> user.getId())
                    .orElseThrow(() -> new RuntimeException("用户未登录或不存在"));

            boolean success = favoriteService.cancelFavoriteById(favoriteId, userId);
            if (success) {
                return ResponseEntity.ok(new ApiResponse(true, "取消收藏成功", null));
            } else {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "取消收藏失败", null));
            }
        } catch (Exception e) {
            logger.error("取消收藏失败", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse(false, "取消收藏失败: " + e.getMessage(), null));
        }
    }




}