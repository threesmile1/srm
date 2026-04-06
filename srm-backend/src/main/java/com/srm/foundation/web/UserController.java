package com.srm.foundation.web;

import com.srm.foundation.domain.Role;
import com.srm.foundation.domain.UserAccount;
import com.srm.foundation.repo.UserAccountRepository;
import com.srm.foundation.service.AuditService;
import com.srm.web.error.BadRequestException;
import com.srm.web.error.NotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "User", description = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final com.srm.foundation.repo.OrgUnitRepository orgUnitRepository;
    private final com.srm.master.repo.SupplierRepository supplierRepository;
    private final com.srm.foundation.repo.RoleRepository roleRepository;
    private final AuditService auditService;

    @GetMapping
    public List<UserResponse> list() {
        return userRepo.findAllByOrderByUsernameAsc().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserCreateRequest req, HttpServletRequest httpReq) {
        if (userRepo.existsByUsername(req.username())) {
            throw new BadRequestException("用户名已存在");
        }
        com.srm.foundation.service.AuthService.validatePasswordStrength(req.password());
        UserAccount u = new UserAccount();
        u.setUsername(req.username());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName());
        u.setEnabled(true);
        if (req.defaultProcurementOrgId() != null) {
            u.setDefaultProcurementOrg(orgUnitRepository.findById(req.defaultProcurementOrgId())
                    .orElseThrow(() -> new NotFoundException("组织不存在")));
        }
        if (req.supplierId() != null) {
            u.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new NotFoundException("供应商不存在")));
        }
        if (req.roleCodes() != null) {
            for (String rc : req.roleCodes()) {
                u.getRoles().add(roleRepository.findByCode(rc)
                        .orElseThrow(() -> new NotFoundException("角色不存在: " + rc)));
            }
        }
        UserAccount saved = userRepo.save(u);

        Long currentUserId = (Long) httpReq.getSession(false).getAttribute(AuthController.SESSION_USER_ID);
        auditService.log(currentUserId, null, "CREATE_USER", "USER", saved.getId(),
                "username=" + saved.getUsername(), httpReq.getRemoteAddr());

        return UserResponse.from(userRepo.findByUsername(saved.getUsername()).orElse(saved));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req,
                                HttpServletRequest httpReq) {
        UserAccount u = userRepo.findById(id).orElseThrow(() -> new NotFoundException("用户不存在"));
        u.setDisplayName(req.displayName());
        u.setEnabled(req.enabled());
        if (req.defaultProcurementOrgId() != null) {
            u.setDefaultProcurementOrg(orgUnitRepository.findById(req.defaultProcurementOrgId())
                    .orElseThrow(() -> new NotFoundException("组织不存在")));
        } else {
            u.setDefaultProcurementOrg(null);
        }
        if (req.supplierId() != null) {
            u.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new NotFoundException("供应商不存在")));
        } else {
            u.setSupplier(null);
        }
        u.getRoles().clear();
        if (req.roleCodes() != null) {
            for (String rc : req.roleCodes()) {
                u.getRoles().add(roleRepository.findByCode(rc)
                        .orElseThrow(() -> new NotFoundException("角色不存在: " + rc)));
            }
        }
        userRepo.save(u);

        Long currentUserId = httpReq.getSession(false) != null
                ? (Long) httpReq.getSession(false).getAttribute(AuthController.SESSION_USER_ID) : null;
        auditService.log(currentUserId, null, "UPDATE_USER", "USER", id,
                "displayName=" + u.getDisplayName(), httpReq.getRemoteAddr());

        return UserResponse.from(userRepo.findByUsername(u.getUsername()).orElse(u));
    }

    @PostMapping("/{id}/reset-password")
    public java.util.Map<String, String> resetPassword(@PathVariable Long id,
                                                         @Valid @RequestBody ResetPasswordRequest req) {
        com.srm.foundation.service.AuthService.validatePasswordStrength(req.newPassword());
        UserAccount u = userRepo.findById(id).orElseThrow(() -> new NotFoundException("用户不存在"));
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepo.save(u);
        return java.util.Map.of("message", "密码已重置");
    }

    public record UserCreateRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @Size(max = 128) String displayName,
            Long defaultProcurementOrgId,
            Long supplierId,
            Set<String> roleCodes
    ) {}

    public record UserUpdateRequest(
            @Size(max = 128) String displayName,
            boolean enabled,
            Long defaultProcurementOrgId,
            Long supplierId,
            Set<String> roleCodes
    ) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 6) String newPassword) {}

    public record UserResponse(Long id, String username, String displayName, boolean enabled,
                                Long defaultProcurementOrgId, Long supplierId, Set<String> roleCodes) {
        static UserResponse from(UserAccount u) {
            return new UserResponse(
                    u.getId(), u.getUsername(), u.getDisplayName(), u.isEnabled(),
                    u.getDefaultProcurementOrg() != null ? u.getDefaultProcurementOrg().getId() : null,
                    u.getSupplier() != null ? u.getSupplier().getId() : null,
                    u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet())
            );
        }
    }
}
