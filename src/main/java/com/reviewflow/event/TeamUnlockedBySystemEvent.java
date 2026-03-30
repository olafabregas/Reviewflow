package com.reviewflow.event;

import com.reviewflow.dto.UserDto;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class TeamUnlockedBySystemEvent extends ApplicationEvent {

    private final Long teamId;
    private final String teamName;
    private final List<UserDto> teamMembers;
    private final String reason;

    public TeamUnlockedBySystemEvent(Object source, Long teamId, String teamName, List<UserDto> teamMembers, String reason) {
        super(source);
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamMembers = teamMembers;
        this.reason = reason;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public List<UserDto> getTeamMembers() {
        return teamMembers;
    }

    public String getReason() {
        return reason;
    }
}
