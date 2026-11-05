package com.teamwork.backend.auth;

import com.teamwork.backend.account.Account;
import com.teamwork.backend.common.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;
    private final CurrentAccount currentAccount;

    public AuthController(AuthService authService, CurrentAccount currentAccount) {
        this.authService = authService;
        this.currentAccount = currentAccount;
    }

    @GetMapping("/api/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/api/auth/register")
    public AccountView register(@Valid @RequestBody RegisterRequest request,
                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Account account = authService.register(request.username(), request.password(), request.displayName(),
                httpRequest, httpResponse);
        return AccountView.from(account);
    }

    @PostMapping("/api/auth/login")
    public AccountView login(@Valid @RequestBody LoginRequest request,
                             HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.login(request.username(), request.password(), httpRequest, httpResponse);
        return AccountView.from(currentAccount.requireAccount());
    }

    @PostMapping("/api/auth/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        currentAccount.requireAccount();
        authService.logout(request);
        return Map.of("message", "已退出登录");
    }

    @GetMapping("/api/auth/me")
    public AccountView me() {
        return AccountView.from(currentAccount.requireAccount());
    }

    public record RegisterRequest(
            @NotBlank(message = "请填写用户名")
            @Size(min = 3, max = 32, message = "用户名长度为 3 到 32 位")
            @Pattern(regexp = "[A-Za-z0-9_]+", message = "用户名只能包含字母、数字和下划线")
            String username,
            @NotBlank(message = "请填写密码")
            @Size(min = 8, max = 72, message = "密码长度为 8 到 72 位")
            String password,
            @NotBlank(message = "请填写显示名")
            @Size(max = 32, message = "显示名最长 32 个字符")
            String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "请填写用户名") String username,
            @NotBlank(message = "请填写密码") String password
    ) {
    }

    public record AccountView(Long id, String username, String displayName) {
        static AccountView from(Account account) {
            return new AccountView(account.getId(), account.getUsername(), account.getDisplayName());
        }
    }
}
