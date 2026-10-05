package com.identity.config;

import com.identity.model.User;
import com.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        initOrUpdateUser("student", "student@example.com", "password123", Set.of("STUDENT"));
        initOrUpdateUser("instructor", "instructor@example.com", "password123", Set.of("INSTRUCTOR"));
        initOrUpdateUser("admin", "admin@example.com", "password123", Set.of("ADMIN"));
        initOrUpdateUser("user", "user@example.com", "password123", Set.of("STUDENT"));

        logger.info(">>> Khởi tạo dữ liệu người dùng mẫu hoàn tất: student, instructor, admin, user (mật khẩu: password123)");
    }

    private void initOrUpdateUser(String username, String email, String rawPassword, Set<String> roles) {
        userRepository.findByUsername(username).ifPresentOrElse(
                user -> {
                    user.setPassword(passwordEncoder.encode(rawPassword));
                    user.setRoles(roles);
                    userRepository.save(user);
                },
                () -> {
                    User newUser = new User(username, email, passwordEncoder.encode(rawPassword), roles);
                    userRepository.save(newUser);
                }
        );
    }
}
