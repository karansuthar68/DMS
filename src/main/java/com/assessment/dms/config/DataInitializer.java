package com.assessment.dms.config;

import com.assessment.dms.dto.user.CreateUserRequest;
import com.assessment.dms.entity.Role;
import com.assessment.dms.repository.UserRepository;
import com.assessment.dms.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-demo-users", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        userService.createUser(new CreateUserRequest(
                "admin", "admin@mycompany.com", "Admin@123", "System Administrator", Role.ADMIN));
        userService.createUser(new CreateUserRequest(
                "john", "john@mycompany.com", "John@1234", "John Carter", Role.USER));
        userService.createUser(new CreateUserRequest(
                "jane", "jane@mycompany.com", "Jane@1234", "Jane Miller", Role.USER));
        log.info("Demo users created: admin / john / jane (passwords are listed in README.md)");
    }
}
