package com.srm.foundation.web;

import com.srm.foundation.domain.Role;
import com.srm.foundation.domain.UserAccount;
import com.srm.foundation.repo.UserAccountRepository;
import com.srm.foundation.service.AuditService;
import com.srm.foundation.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Auth", description = "认证与会话")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String SESSION_USER_ID = "SRM_USER_ID";
    public static final String SESSION_USERNAME = "SRM_USERNAME";
    public static final String SESSION_ROLES = "SRM_ROLES";
    public static final String SESSION_SUPPLIER_ID = "SRM_SUPPLIER_ID";
    public static final String SESSION_SUPPLIER_NAME = "SRM_SUPPLIER_NAME";
    public static final String SESSION_DEFAULT_ORG_ID = "SRM_DEFAULT_ORG_ID";
    public static final String SESSION_DISPLAY_NAME = "SRM_DISPLAY_NAME";

    private final AuthService authService;
    private final AuditService auditService;
    private final UserAccountRepository userAccountRepository;

    @PostMapping("/login")
    public UserInfoResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        UserAccount user = authService.authenticate(req.username(), req.password());
        HttpSession session = httpReq.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setAttribute(SESSION_USERNAME, user.getUsername());
        String displayName = user.getDisplayName();
        session.setAttribute(SESSION_DISPLAY_NAME,
                displayName != null && !displayName.isBlank() ? displayName : user.getUsername());
        Set<String> roleCodes = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        session.setAttribute(SESSION_ROLES, roleCodes);
        if (user.getSupplier() != null) {
            session.setAttribute(SESSION_SUPPLIER_ID, user.getSupplier().getId());
            session.setAttribute(SESSION_SUPPLIER_NAME, user.getSupplier().getName());
        }
        if (user.getDefaultProcurementOrg() != null) {
            session.setAttribute(SESSION_DEFAULT_ORG_ID, user.getDefaultProcurementOrg().getId());
        }
        auditService.log(user.getId(), user.getUsername(), "LOGIN", "USER", user.getId(),
                null, httpReq.getRemoteAddr());
        return UserInfoResponse.from(user);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest httpReq) {
        HttpSession session = httpReq.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("message", "已退出");
    }

    @GetMapping("/me")
    public UserInfoResponse me(HttpServletRequest httpReq) {
        HttpSession session = httpReq.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER_ID) == null) {
            throw new com.srm.web.error.BadRequestException("未登录");
        }
        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        String username = (String) session.getAttribute(SESSION_USERNAME);
        String displayName = (String) session.getAttribute(SESSION_DISPLAY_NAME);
        if (displayName == null || displayName.isBlank()) {
            displayName = username;
        }
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) session.getAttribute(SESSION_ROLES);
        Long supplierId = (Long) session.getAttribute(SESSION_SUPPLIER_ID);
        String supplierName = (String) session.getAttribute(SESSION_SUPPLIER_NAME);
        if (supplierId != null && (supplierName == null || supplierName.isBlank())) {
            supplierName = userAccountRepository.findWithSupplierById(userId)
                    .map(UserAccount::getSupplier)
                    .map(s -> s != null ? s.getName() : null)
                    .orElse(null);
            if (supplierName != null && !supplierName.isBlank()) {
                session.setAttribute(SESSION_SUPPLIER_NAME, supplierName);
            }
        }
        Long defaultOrgId = (Long) session.getAttribute(SESSION_DEFAULT_ORG_ID);
        return new UserInfoResponse(userId, username, displayName, roles, defaultOrgId, supplierId, supplierName);
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                               HttpServletRequest httpReq) {
        HttpSession session = httpReq.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER_ID) == null) {
            throw new com.srm.web.error.BadRequestException("未登录");
        }
        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        authService.changePassword(userId, req.oldPassword(), req.newPassword());
        auditService.log(userId, (String) session.getAttribute(SESSION_USERNAME),
                "CHANGE_PASSWORD", "USER", userId, null, httpReq.getRemoteAddr());
        return Map.of("message", "密码已修改");
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(max = 128) String newPassword) {}

    public record UserInfoResponse(
            Long id, String username, String displayName,
            Set<String> roles, Long defaultProcurementOrgId, Long supplierId, String supplierName
    ) {
        static UserInfoResponse from(UserAccount u) {
            return new UserInfoResponse(
                    u.getId(),
                    u.getUsername(),
                    u.getDisplayName(),
                    u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                    u.getDefaultProcurementOrg() != null ? u.getDefaultProcurementOrg().getId() : null,
                    u.getSupplier() != null ? u.getSupplier().getId() : null,
                    u.getSupplier() != null ? u.getSupplier().getName() : null
            );
        }
    }
}
