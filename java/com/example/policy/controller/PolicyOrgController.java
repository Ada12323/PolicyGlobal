package com.example.policy.controller;

import com.example.policy.dto.PolicyDataWithRatingDTO;
import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import com.example.policy.service.PolicyOrgService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController

public class PolicyOrgController {
    private final PolicyOrgService policyOrgService;

    public PolicyOrgController(PolicyOrgService policyOrgService) {
        this.policyOrgService = policyOrgService;
    }

    @GetMapping("/org/someorgs")
    public List<PolicyOrg> someOrgs() {
        return policyOrgService.findsomeOrg();
    }

    @GetMapping("/org/someorgs-mysearch")
    public List<PolicyOrg> someOrgsMySearch() {
        return policyOrgService.findsomeOrgMySearch();
    }

    @GetMapping("/org/getpolicy")
    public List<PolicyData> getPolicy(String orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return null;
        }
      return policyOrgService.findPolicyDataByOrgIds(orgIds);
    }

    @GetMapping("/org/getpolicy-with-rating")
    public List<PolicyDataWithRatingDTO> getPolicyWithRating(@RequestParam String orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return null;
        }
        return policyOrgService.findPolicyDataWithRatingByOrgIds(orgIds);
    }

    @GetMapping("/org/getpolicy-mysearch")
    public List<PolicyDataWithRatingDTO> getMyPolicy(@RequestParam String orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return null;
        }
        return policyOrgService.findPolicyDataWithRatingAndUserIdByOrgIds(orgIds);
    }
}
