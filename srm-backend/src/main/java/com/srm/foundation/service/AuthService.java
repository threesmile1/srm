package com.srm.foundation.service;

import com.srm.foundation.domain.UserAccount;
import com.srm.foundation.repo.UserAccountRepository;
import com.srm.web.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserAccount authenticate(String username, String password) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("用户名或密码错误"));
        if (!user.isEnabled()) {
            throw new BadRequestException("账号已禁用");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("用户名或密码错误");
        }
        return user;
    }

    @Transactional
    public UserAccount createUser(String username, String password, String displayName,
                                   Long defaultOrgId, Long supplierId) {
        if (userAccountRepository.existsByUsername(username)) {
            throw new BadRequestException("用户名已存在: " + username);
        }
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDisplayName(displayName);
        return userAccountRepository.save(u);
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        UserAccount u = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, u.getPasswordHash())) {
            throw new BadRequestException("原密码错误");
        }
        validatePasswordStrength(newPassword);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(u);
    }

    /** 仅校验非空；复杂度与长度上限由接口层 {@code @Size} 等约束。 */
    public static void validatePasswordStrength(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("密码不能为空");
        }
    }
}
