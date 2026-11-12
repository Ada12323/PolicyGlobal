package com.example.policy.controller;

import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import com.example.policy.entity.Role;
import com.example.policy.entity.User;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.repository.PolicyOrgRepository;
import com.example.policy.repository.RoleRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.DedupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final PolicyDataRepository policyDataRepository;
    private final PolicyOrgRepository policyOrgRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DedupService dedupService;

    public AdminController(PolicyDataRepository policyDataRepository, PolicyOrgRepository policyOrgRepository,
                           UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                           DedupService dedupService) {
        this.policyDataRepository = policyDataRepository;
        this.policyOrgRepository = policyOrgRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.dedupService = dedupService;
    }

    // -------- 机构管理 --------
    @GetMapping("/orgs")
    public List<PolicyOrg> listOrgs() { return policyOrgRepository.findAll(); }

    @PostMapping("/orgs")
    public PolicyOrg addOrg(@RequestBody Map<String,String> body) {
        PolicyOrg o = new PolicyOrg();
        o.setOrgName(body.get("orgName"));
        o.setSampleUrl(body.get("sampleUrl"));
        return policyOrgRepository.save(o);
    }

    @PutMapping("/orgs/{id}")
    public PolicyOrg updateOrg(@PathVariable("id") Long id, @RequestBody Map<String,String> body) {
        PolicyOrg o = policyOrgRepository.findById(id).orElseThrow();
        if (body.containsKey("orgName")) o.setOrgName(body.get("orgName"));
        if (body.containsKey("sampleUrl")) o.setSampleUrl(body.get("sampleUrl"));
        return policyOrgRepository.save(o);
    }

    @DeleteMapping("/orgs/{id}")
    public ResponseEntity<?> deleteOrg(@PathVariable("id") Long id) {
        policyOrgRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // -------- 政策数据管理 --------
    @PostMapping("/policies")
    public PolicyData addPolicy(@RequestBody Map<String,Object> body) {
        PolicyData p = new PolicyData();
        p.setZhTitle((String) body.get("zhTitle"));
        p.setOriginalTitle((String) body.get("originalTitle"));
        p.setSummary((String) body.get("summary"));
        if (body.get("publishDate") != null) p.setPublishDate(LocalDate.parse(body.get("publishDate").toString()));
        p.setSourceUrl((String) body.get("sourceUrl"));
        p.setPdfUrl((String) body.get("pdfUrl"));
        if (body.get("orgId") != null) {
            policyOrgRepository.findById(Long.valueOf(body.get("orgId").toString())).ifPresent(p::setOrg);
        }
        return policyDataRepository.save(p);
    }

    @PutMapping("/policies/{id}")
    public PolicyData updatePolicy(@PathVariable("id") Long id, @RequestBody Map<String,Object> body) {
        PolicyData p = policyDataRepository.findById(id).orElseThrow();
        if (body.containsKey("zhTitle")) p.setZhTitle((String) body.get("zhTitle"));
        if (body.containsKey("originalTitle")) p.setOriginalTitle((String) body.get("originalTitle"));
        if (body.containsKey("summary")) p.setSummary((String) body.get("summary"));
        if (body.containsKey("publishDate")) p.setPublishDate(LocalDate.parse(body.get("publishDate").toString()));
        if (body.containsKey("sourceUrl")) p.setSourceUrl((String) body.get("sourceUrl"));
        if (body.containsKey("pdfUrl")) p.setPdfUrl((String) body.get("pdfUrl"));
        if (body.containsKey("orgId")) {
            if (body.get("orgId") == null) p.setOrg(null); else policyOrgRepository.findById(Long.valueOf(body.get("orgId").toString())).ifPresent(p::setOrg);
        }
        return policyDataRepository.save(p);
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<?> deletePolicy(@PathVariable("id") Long id) {
        policyDataRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // 一键去重（按 pdf_url 规范化）
    @PostMapping("/policies/deduplicate")
    public Map<String,Object> deduplicatePolicies() {
        DedupService.DedupResult r = dedupService.deduplicateByPdfUrl();
        Map<String,Object> resp = new HashMap<>();
        resp.put("groups", r.groups);
        resp.put("removed", r.removed);
        resp.put("retained", r.retained);
        resp.put("mergedClicks", r.mergedClicks);
        resp.put("details", r.details);
        return resp;
    }

    // -------- 用户管理 --------
    @GetMapping("/users")
    public List<Map<String,Object>> listUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String,Object>> res = new ArrayList<>();
        for (User u : users) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("status", u.getStatus());
            m.put("roles", u.getRoles()!=null? u.getRoles().stream().map(Role::getCode).toList(): List.of());
            res.add(m);
        }
        return res;
    }

    @PostMapping("/users")
    public Map<String,Object> addUser(@RequestBody Map<String,Object> body) {
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String nickname = (String) body.get("nickname");
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setNickname(nickname);
        u.setStatus(1);
        userRepository.save(u);
        Map<String,Object> r = new HashMap<>();
        r.put("id", u.getId());
        return r;
    }



    @PutMapping("/users/{id}/roles")
    @Transactional
    public ResponseEntity<?> updateUserRoles(@PathVariable("id") Long id, @RequestBody Map<String,List<String>> body) {
        User u = userRepository.findById(id).orElseThrow();
        List<String> codes = body.getOrDefault("roles", List.of());
        Set<Role> newRoles = new HashSet<>();
        for (String c : codes) {
            roleRepository.findByCode(c).ifPresent(newRoles::add);
        }
        u.setRoles(newRoles);
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String,Integer> body) {
        User u = userRepository.findById(id).orElseThrow();
        if (body.containsKey("status")) u.setStatus(body.get("status"));
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }


    @PutMapping("/users/{id}/set-role")
    @Transactional
    public ResponseEntity<?> setUserRole(@PathVariable("id") Long id, @RequestBody Map<String,Integer> body) {
        User u = userRepository.findById(id).orElseThrow();
        String roleCode = body.get("roleType") == 1 ? "ADMIN" : "USER";
        Set<Role> newRoles = new HashSet<>();
        roleRepository.findByCode(roleCode).ifPresent(newRoles::add);
        u.setRoles(newRoles);

        userRepository.save(u);
        return ResponseEntity.ok().build();
    }
}