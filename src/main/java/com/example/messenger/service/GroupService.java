package com.example.messenger.service;

import com.example.messenger.dto.CreateGroupRequest;
import com.example.messenger.dto.GroupDto;
import com.example.messenger.dto.GroupMemberDto;
import com.example.messenger.model.ChatGroup;
import com.example.messenger.model.GroupMember;
import com.example.messenger.model.GroupType;
import com.example.messenger.model.User;
import com.example.messenger.repository.ChatGroupRepository;
import com.example.messenger.repository.GroupMemberRepository;
import com.example.messenger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final ChatGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PlatformSettingsService platformSettingsService;

    public GroupService(ChatGroupRepository groupRepository, GroupMemberRepository memberRepository,
                         UserRepository userRepository, PlatformSettingsService platformSettingsService) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.platformSettingsService = platformSettingsService;
    }

    public GroupDto createGroup(String creator, CreateGroupRequest request) {
        boolean creatorIsAdmin = userRepository.findByUsername(creator).map(User::isAdmin).orElse(false);
        if (!platformSettingsService.isGroupCreationEnabled() && !creatorIsAdmin) {
            throw new IllegalArgumentException("Создание групп временно отключено администратором");
        }
        // CHANNEL used to be creatable here too (public channels, join-by-anyone); that concept
        // was replaced by the News feed. The enum value is kept only so any pre-existing CHANNEL
        // rows in the database still deserialize correctly — it's no longer creatable via the API.
        if (!"GROUP".equalsIgnoreCase(request.getType() == null ? "" : request.getType().trim())) {
            throw new IllegalArgumentException("type must be GROUP");
        }
        GroupType type = GroupType.GROUP;

        ChatGroup group = new ChatGroup(request.getName().trim(), type, creator);
        group = groupRepository.save(group);

        memberRepository.save(new GroupMember(group, creator, "ADMIN"));

        if (request.getMembers() != null) {
            for (String username : request.getMembers()) {
                if (username != null && !username.equals(creator) && !memberRepository.existsByGroupIdAndUsername(group.getId(), username)) {
                    memberRepository.save(new GroupMember(group, username));
                }
            }
        }

        return toDto(group, creator);
    }

    public List<GroupMemberDto> listMembers(Long groupId, String requester) {
        if (!isMember(groupId, requester)) {
            throw new SecurityException("Not a member of this group");
        }
        return memberRepository.findByGroupId(groupId).stream()
                .map(m -> {
                    var u = userRepository.findByUsername(m.getUsername());
                    return new GroupMemberDto(m.getUsername(),
                            u.map(User::getDisplayName).orElse(m.getUsername()),
                            u.map(User::getAvatarUrl).orElse(null),
                            m.getRole());
                })
                .collect(Collectors.toList());
    }

    /** Leaving is always allowed; the last admin can't leave without promoting someone else first. */
    public void leaveGroup(Long groupId, String username) {
        GroupMember member = memberRepository.findByGroupIdAndUsername(groupId, username)
                .orElseThrow(() -> new IllegalArgumentException("Not a member of this group"));
        if (member.isAdmin()) {
            long adminCount = memberRepository.findByGroupId(groupId).stream().filter(GroupMember::isAdmin).count();
            long total = memberRepository.countByGroupId(groupId);
            if (adminCount <= 1 && total > 1) {
                throw new IllegalArgumentException("Назначьте другого админа перед выходом");
            }
        }
        memberRepository.delete(member);
        if (memberRepository.countByGroupId(groupId) == 0) {
            groupRepository.deleteById(groupId);
        }
    }

    /** Only a group admin can delete the whole group. */
    public void deleteGroup(Long groupId, String requester) {
        GroupMember member = memberRepository.findByGroupIdAndUsername(groupId, requester)
                .orElseThrow(() -> new SecurityException("Not a member of this group"));
        if (!member.isAdmin()) {
            throw new SecurityException("Only a group admin can delete the group");
        }
        memberRepository.deleteAll(memberRepository.findByGroupId(groupId));
        groupRepository.deleteById(groupId);
    }

    /** Only a group admin can kick another member. */
    public void kickMember(Long groupId, String requester, String target) {
        GroupMember requesterMember = memberRepository.findByGroupIdAndUsername(groupId, requester)
                .orElseThrow(() -> new SecurityException("Not a member of this group"));
        if (!requesterMember.isAdmin()) {
            throw new SecurityException("Only a group admin can remove members");
        }
        if (requester.equals(target)) {
            throw new IllegalArgumentException("Use leave instead of kicking yourself");
        }
        GroupMember targetMember = memberRepository.findByGroupIdAndUsername(groupId, target)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member"));
        memberRepository.delete(targetMember);
    }

    /** Only a group admin can promote/demote another member's role. */
    public void setMemberRole(Long groupId, String requester, String target, String role) {
        GroupMember requesterMember = memberRepository.findByGroupIdAndUsername(groupId, requester)
                .orElseThrow(() -> new SecurityException("Not a member of this group"));
        if (!requesterMember.isAdmin()) {
            throw new SecurityException("Only a group admin can change roles");
        }
        if (!"ADMIN".equals(role) && !"MEMBER".equals(role)) {
            throw new IllegalArgumentException("role must be ADMIN or MEMBER");
        }
        GroupMember targetMember = memberRepository.findByGroupIdAndUsername(groupId, target)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member"));
        targetMember.setRole(role);
        memberRepository.save(targetMember);
    }

    public List<GroupDto> listMine(String username) {
        return memberRepository.findByUsername(username).stream()
                .map(m -> toDto(m.getGroup(), username))
                .collect(Collectors.toList());
    }

    public ChatGroup getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
    }

    public boolean isMember(Long groupId, String username) {
        return memberRepository.existsByGroupIdAndUsername(groupId, username);
    }

    public List<String> memberUsernames(Long groupId) {
        return memberRepository.findByGroupId(groupId).stream()
                .map(GroupMember::getUsername)
                .collect(Collectors.toList());
    }

    private GroupDto toDto(ChatGroup g, String viewer) {
        long count = memberRepository.countByGroupId(g.getId());
        boolean isMember = memberRepository.existsByGroupIdAndUsername(g.getId(), viewer);
        return new GroupDto(g.getId(), g.getName(), g.getAvatarUrl(), g.getType(), g.getCreatedBy(), count, isMember);
    }
}
