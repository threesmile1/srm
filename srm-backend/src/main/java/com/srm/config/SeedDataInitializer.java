package com.srm.config;

import com.srm.foundation.domain.Role;
import com.srm.foundation.domain.UserAccount;
import com.srm.foundation.repo.RoleRepository;
import com.srm.foundation.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedDataInitializer implements ApplicationRunner {

    private final UserAccountRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdminUser();
    }

    private void seedAdminUser() {
        if (userRepo.existsByUsername("admin")) return;

        Role adminRole = roleRepo.findByCode("ADMIN").orElse(null);
        if (adminRole == null) {
            log.warn("ADMIN 角色未找到，跳过 admin 用户初始化");
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setDisplayName("系统管理员");
        admin.setEnabled(true);
        admin.getRoles().add(adminRole);
        userRepo.save(admin);
        log.info("已创建默认管理员 admin / admin123");
    }
}
