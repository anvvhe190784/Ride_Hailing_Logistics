package com.ridehailing.logistics.iam.mapper;

import com.ridehailing.logistics.iam.domain.entity.User;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
