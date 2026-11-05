package com.teamwork.backend.team;

import com.teamwork.backend.common.CurrentAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TeamController {

    private final TeamService teamService;
    private final CurrentAccount currentAccount;

    public TeamController(TeamService teamService, CurrentAccount currentAccount) {
        this.teamService = teamService;
        this.currentAccount = currentAccount;
    }

    @GetMapping("/api/team")
    public ResponseEntity<?> current() {
        TeamMember member = currentAccount.optionalMember();
        if (member == null) {
            return ResponseEntity.status(404).body(Map.of("message", "尚未加入团队"));
        }
        return ResponseEntity.ok(teamService.view(member));
    }

    @PostMapping("/api/team")
    public TeamService.TeamView create(@Valid @RequestBody NameRequest request) {
        return teamService.create(currentAccount.requireAccount(), request.name());
    }

    @PostMapping("/api/team/join")
    public TeamService.TeamView join(@Valid @RequestBody JoinRequest request) {
        return teamService.join(currentAccount.requireAccount(), request.code());
    }

    @PostMapping("/api/team/invite")
    public TeamService.TeamView regenerate() {
        return teamService.regenerateInvite(currentAccount.requireCreator());
    }

    public record NameRequest(
            @NotBlank(message = "请填写团队名称")
            @Size(max = 64, message = "团队名称最长 64 个字符")
            String name
    ) {
    }

    public record JoinRequest(
            @NotBlank(message = "请填写邀请码")
            @Pattern(regexp = "[A-Za-z0-9]{5}", message = "邀请码为 5 位字母或数字")
            String code
    ) {
    }
}
