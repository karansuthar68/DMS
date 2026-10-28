package com.assessment.dms.controller;

import com.assessment.dms.dto.user.UserResponse;
import com.assessment.dms.security.UserPrincipal;
import com.assessment.dms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        return userService.getUser(currentUser.getId());
    }
}
