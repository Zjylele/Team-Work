package com.teamwork.backend.account;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProfileController {

    private final AccountMapper accounts;
    private final CurrentAccount currentAccount;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(AccountMapper accounts, CurrentAccount currentAccount, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.currentAccount = currentAccount;
        this.passwordEncoder = passwordEncoder;
    }

    @PatchMapping("/api/me")
    @Transactional
    public Map<String, String> updateDisplayName(@Valid @RequestBody DisplayNameRequest request) {
        Account account = currentAccount.requireAccount();
        account.setDisplayName(request.displayName());
        accounts.updateById(account);
        return Map.of("displayName", account.getDisplayName());
    }

    @PostMapping("/api/me/password")
    @Transactional
    public Map<String, String> changePassword(@Valid @RequestBody PasswordRequest request) {
        Account account = currentAccount.requireAccount();
        if (!passwordEncoder.matches(request.oldPassword(), account.getPasswordHash())) {
            throw new ApiException(400, "原密码不正确");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        accounts.updateById(account);
        return Map.of("message", "密码已更新");
    }

    public record DisplayNameRequest(
            @NotBlank(message = "请填写显示名")
            @Size(max = 32, message = "显示名最长 32 个字符")
            String displayName
    ) {
    }

    public record PasswordRequest(
            @NotBlank(message = "请填写原密码") String oldPassword,
            @NotBlank(message = "请填写新密码")
            @Size(min = 8, max = 72, message = "密码长度为 8 到 72 位")
            String newPassword
    ) {
    }
}
