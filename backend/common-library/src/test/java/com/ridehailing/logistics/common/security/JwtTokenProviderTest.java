package com.ridehailing.logistics.common.security;

import com.ridehailing.logistics.common.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
    }

    @Test
    void shouldGenerateAndValidateAccessTokenSuccessfully() {
        UUID userId = UUID.randomUUID();
        String phone = "+84987654321";
        UserRole role = UserRole.DRIVER;

        String token = jwtTokenProvider.generateAccessToken(userId, phone, role);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(UserRole.DRIVER.name(), jwtTokenProvider.getRoleFromToken(token));
        assertEquals(phone, jwtTokenProvider.getPhoneFromToken(token));
    }

    @Test
    void shouldGenerateAndValidateRefreshTokenSuccessfully() {
        UUID userId = UUID.randomUUID();

        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(refreshToken));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(refreshToken));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.string"));
    }
}
