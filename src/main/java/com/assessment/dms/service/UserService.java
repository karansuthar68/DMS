package com.assessment.dms.service;

import com.assessment.dms.dto.user.CreateUserRequest;
import com.assessment.dms.dto.user.UpdateUserRequest;
import com.assessment.dms.dto.user.UserResponse;
import com.assessment.dms.entity.Role;
import com.assessment.dms.entity.User;
import com.assessment.dms.exception.BadRequestException;
import com.assessment.dms.exception.DuplicateResourceException;
import com.assessment.dms.exception.ResourceNotFoundException;
import com.assessment.dms.repository.DocumentRepository;
import com.assessment.dms.repository.UserRepository;
import com.assessment.dms.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());

        User savedUser = userRepository.save(user);
        log.info("Created user '{}' with role {}", savedUser.getUsername(), savedUser.getRole());
        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll(Sort.by("id")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request, UserPrincipal currentUser) {
        User user = findUser(userId);

        boolean isSelf = user.getId().equals(currentUser.getId());
        if (isSelf && (request.role() != Role.ADMIN || !request.enabled())) {
            throw new BadRequestException("You cannot remove your own admin role or disable your own account");
        }

        String email = normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        user.setEmail(email);
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        log.info("User '{}' updated by '{}'", user.getUsername(), currentUser.getUsername());
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long userId, UserPrincipal currentUser) {
        if (userId.equals(currentUser.getId())) {
            throw new BadRequestException("You cannot delete your own account");
        }
        User user = findUser(userId);
        if (documentRepository.existsByOwnerId(userId)) {
            throw new BadRequestException(
                    "User owns documents. Delete those documents first, or disable the account instead");
        }
        userRepository.delete(user);
        log.info("User '{}' deleted by '{}'", user.getUsername(), currentUser.getUsername());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
