package com.teamwork.backend.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamwork.backend.account.Account;
import com.teamwork.backend.account.AccountMapper;
import com.teamwork.backend.team.TeamMember;
import com.teamwork.backend.team.TeamMemberMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccount {

    private final AccountMapper accounts;
    private final TeamMemberMapper members;

    public CurrentAccount(AccountMapper accounts, TeamMemberMapper members) {
        this.accounts = accounts;
        this.members = members;
    }

    public Account requireAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new ApiException(401, "未登录");
        }
        Account account = accounts.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUsername, authentication.getName()));
        if (account == null) {
            throw new ApiException(401, "未登录");
        }
        return account;
    }

    public TeamMember optionalMember() {
        Account account = requireAccount();
        return members.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getAccountId, account.getId()));
    }

    public TeamMember requireMember() {
        TeamMember member = optionalMember();
        if (member == null) {
            throw new ApiException(403, "尚未加入团队");
        }
        return member;
    }

    public TeamMember requireCreator() {
        TeamMember member = requireMember();
        if (!TeamMember.CREATOR.equals(member.getRole())) {
            throw new ApiException(403, "只有创建者可以执行该操作");
        }
        return member;
    }
}
