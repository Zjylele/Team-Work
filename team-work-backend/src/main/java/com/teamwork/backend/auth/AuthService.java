package com.teamwork.backend.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamwork.backend.account.Account;
import com.teamwork.backend.account.AccountMapper;
import com.teamwork.backend.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AccountMapper accounts;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(AccountMapper accounts, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @Transactional
    public Account register(String username, String password, String displayName,
                            HttpServletRequest request, HttpServletResponse response) {
        Long existing = accounts.selectCount(new LambdaQueryWrapper<Account>().eq(Account::getUsername, username));
        if (existing != null && existing > 0) {
            throw new ApiException(409, "用户名已存在");
        }
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setDisplayName(displayName);
        account.setCreatedAt(LocalDateTime.now());
        accounts.insert(account);
        establishSession(username, password, request, response);
        return account;
    }

    public void login(String username, String password, HttpServletRequest request, HttpServletResponse response) {
        establishSession(username, password, request, response);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void establishSession(String username, String password,
                                  HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
            securityContextRepository.saveContext(context, request, response);
        } catch (AuthenticationException exception) {
            throw new ApiException(401, "用户名或密码错误");
        }
    }
}
