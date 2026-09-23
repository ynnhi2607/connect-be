package com.connect.be.core.auth.dto;

import com.connect.be.core.user.dto.UserResponse;
import java.time.Instant;

public record AuthResponse(
        String token,
        Instant expiresAt,
        UserResponse user
) {
}
