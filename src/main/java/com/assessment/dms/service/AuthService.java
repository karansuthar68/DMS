package com.assessment.dms.service;

import com.assessment.dms.dto.auth.AuthResponse;
import com.assessment.dms.dto.auth.LoginRequest;
import com.assessment.dms.dto.auth.RegisterRequest;
import com.assessment.dms.dto.user.CreateUserRequest;
import com.assessment.dms.dto.user.UserResponse;
import com.assessment.dms.entity.Role;
import com.assessment.dms.security.JwtService;
import com.assessment.dms.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getTokenValiditySeconds(),
                user.getUsername(), user.getRole());
    }

    public UserResponse register(RegisterRequest request) {
        return userService.createUser(new CreateUserRequest(
                request.username(), request.email(), request.password(), request.fullName(), Role.USER));
    }
}
