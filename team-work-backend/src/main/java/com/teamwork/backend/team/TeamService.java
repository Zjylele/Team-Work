package com.teamwork.backend.team;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamwork.backend.account.Account;
import com.teamwork.backend.account.AccountMapper;
import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.InviteAttemptLimiter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeamService {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 5;

    private final TeamMapper teams;
    private final TeamMemberMapper members;
    private final AccountMapper accounts;
    private final InviteAttemptLimiter inviteAttemptLimiter;
    private final SecureRandom random = new SecureRandom();

    public TeamService(TeamMapper teams, TeamMemberMapper members, AccountMapper accounts,
                       InviteAttemptLimiter inviteAttemptLimiter) {
        this.teams = teams;
        this.members = members;
        this.accounts = accounts;
        this.inviteAttemptLimiter = inviteAttemptLimiter;
    }

    @Transactional
    public TeamView create(Account account, String name) {
        if (membership(account.getId()) != null) {
            throw new ApiException(409, "已经在团队中");
        }
        if (teams.selectCount(null) > 0) {
            throw new ApiException(409, "团队已存在");
        }
        Team team = new Team();
        team.setName(name);
        team.setInviteCode(newCode());
        team.setCreatedBy(account.getId());
        team.setSingleRow(1);
        team.setCreatedAt(LocalDateTime.now());
        try {
            teams.insert(team);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(409, "团队已存在");
        }
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setAccountId(account.getId());
        member.setRole(TeamMember.CREATOR);
        member.setCreatedAt(LocalDateTime.now());
        members.insert(member);
        return TeamView.of(team, member.getRole(), true);
    }

    @Transactional
    public TeamView join(Account account, String code) {
        if (membership(account.getId()) != null) {
            throw new ApiException(409, "已经在团队中");
        }
        inviteAttemptLimiter.check(account.getId());
        Team team = teams.selectOne(new LambdaQueryWrapper<Team>()
                .eq(Team::getInviteCode, code.trim().toUpperCase()));
        if (team == null) {
            inviteAttemptLimiter.fail(account.getId());
            throw new ApiException(400, "邀请码不正确");
        }
        inviteAttemptLimiter.succeed(account.getId());
        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setAccountId(account.getId());
        member.setRole(TeamMember.MEMBER);
        member.setCreatedAt(LocalDateTime.now());
        members.insert(member);
        return TeamView.of(team, member.getRole(), false);
    }

    public TeamView view(TeamMember member) {
        Team team = requireTeam(member.getTeamId());
        return TeamView.of(team, member.getRole(), TeamMember.CREATOR.equals(member.getRole()));
    }

    public List<MemberView> listMembers(TeamMember actor) {
        List<TeamMember> memberships = members.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, actor.getTeamId())
                .orderByAsc(TeamMember::getId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> accountIds = memberships.stream().map(TeamMember::getAccountId).toList();
        Map<Long, Account> byId = accounts.selectList(new LambdaQueryWrapper<Account>().in(Account::getId, accountIds))
                .stream()
                .collect(Collectors.toMap(Account::getId, account -> account));
        return memberships.stream().map(membership -> {
            Account account = byId.get(membership.getAccountId());
            String displayName = account == null ? "" : account.getDisplayName();
            return new MemberView(membership.getAccountId(), displayName, membership.getRole());
        }).toList();
    }

    @Transactional
    public TeamView regenerateInvite(TeamMember creator) {
        Team team = requireTeam(creator.getTeamId());
        team.setInviteCode(newCode());
        teams.updateById(team);
        return TeamView.of(team, creator.getRole(), true);
    }

    public Team requireTeam(Long teamId) {
        Team team = teams.selectById(teamId);
        if (team == null) {
            throw new ApiException(404, "团队不存在");
        }
        return team;
    }

    private TeamMember membership(Long accountId) {
        return members.selectOne(new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getAccountId, accountId));
    }

    private String newCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String value = code.toString();
            Long existing = teams.selectCount(new LambdaQueryWrapper<Team>().eq(Team::getInviteCode, value));
            if (existing == null || existing == 0) {
                return value;
            }
        }
        throw new ApiException(500, "邀请码生成失败");
    }

    public record TeamView(Long id, String name, String role, String inviteCode) {
        static TeamView of(Team team, String role, boolean includeCode) {
            return new TeamView(team.getId(), team.getName(), role, includeCode ? team.getInviteCode() : null);
        }
    }

    public record MemberView(Long id, String displayName, String role) {
    }
}
