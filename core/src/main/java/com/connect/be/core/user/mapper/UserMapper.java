package com.connect.be.core.user.mapper;

import com.connect.be.core.user.dto.UserResponse;
import com.connect.be.core.user.model.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
