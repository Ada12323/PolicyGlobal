package com.example.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrgCreateRequest {
    @NotBlank(message = "机构名称不能为空")
    @Size(max = 255, message = "机构名称长度不能超过255字符")
    private String orgName;

    @Size(max = 1000, message = "参考链接长度不能超过1000字符")
    private String sampleUrl;
}
