package com.zoee.equipops.auth.domain.vo;

import lombok.Data;

@Data
public class TokenVO {
    private String accessToken;   // JWT 字符串，前端塞 Header 里
    private String tokenType;     // 固定 "Bearer"
    private Long expiresIn;       // 还有多久过期（秒），前端看快到点了就调 refresh
}
