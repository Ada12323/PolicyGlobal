package com.example.policy.service;

import com.example.policy.dto.PolicyDataWithRatingDTO;
import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;

import java.util.List;

public interface PolicyOrgService {
    List<PolicyOrg> findsomeOrg();
    List<PolicyData>findPolicyDataByOrgIds(String orgIds);
    List<PolicyDataWithRatingDTO> findPolicyDataWithRatingByOrgIds(String orgIds);
    //筛选当前用户检索的政策数据
    List<PolicyDataWithRatingDTO> findPolicyDataWithRatingAndUserIdByOrgIds(String orgIds);
    //筛选当前用户检索的政策机构
    List<PolicyOrg> findsomeOrgMySearch();
}
