package com.example.messenger.service;

import com.example.messenger.dto.RegisterRequest;
import com.example.messenger.dto.UpdateProfileRequest;
import com.example.messenger.dto.UserDto;
import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PresenceService presenceService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, presenceService);
    }

    private User userWith(String username, boolean showOnlineStatus) {
        User u = new User(username, "hash", "Display " + username);
        u.setLastSeen(Instant.parse("2026-01-01T00:00:00Z"));
        u.setShowOnlineStatus(showOnlineStatus);
        return u;
    }

    @Test
    void register_usernameTaken_throws() {
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("bob");
        req.setPassword("secret1");
        req.setDisplayName("Bob");

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_newUsername_savesEncodedPassword() {
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("ENCODED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest req = new RegisterRequest();
        req.setUsername("newbie");
        req.setPassword("secret1");
        req.setDisplayName("Newbie");

        User saved = userService.register(req);

        assertThat(saved.getPassword()).isEqualTo("ENCODED");
        assertThat(saved.getUsername()).isEqualTo("newbie");
    }

    @Test
    void listContacts_hidesOnlineStatus_forUsersWhoOptedOut() {
        User privateUser = userWith("private_bob", false);
        when(userRepository.findAll()).thenReturn(List.of(privateUser));
        when(presenceService.isOnline("private_bob")).thenReturn(true); // actually online...

        List<UserDto> contacts = userService.listContacts("me");

        // ...but since they opted out and we're not viewing our own profile, it must be hidden.
        assertThat(contacts).hasSize(1);
        UserDto dto = contacts.get(0);
        assertThat(dto.isOnline()).isFalse();
        assertThat(dto.getLastSeen()).isNull();
    }

    @Test
    void listContacts_showsOnlineStatus_forUsersWhoOptedIn() {
        User publicUser = userWith("public_alice", true);
        when(userRepository.findAll()).thenReturn(List.of(publicUser));
        when(presenceService.isOnline("public_alice")).thenReturn(true);

        List<UserDto> contacts = userService.listContacts("me");

        assertThat(contacts.get(0).isOnline()).isTrue();
        assertThat(contacts.get(0).getLastSeen()).isNotNull();
    }

    @Test
    void listContacts_excludesSelf() {
        User me = userWith("me", true);
        User other = userWith("other", true);
        when(userRepository.findAll()).thenReturn(List.of(me, other));
        lenient().when(presenceService.isOnline(anyString())).thenReturn(false);

        List<UserDto> contacts = userService.listContacts("me");

        assertThat(contacts).extracting(UserDto::getUsername).containsExactly("other");
    }

    @Test
    void getMe_alwaysShowsOwnTrueStatus_evenIfPrivacyOff() {
        User privateSelf = userWith("me", false);
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(privateSelf));
        when(presenceService.isOnline("me")).thenReturn(true);

        UserDto dto = userService.getMe("me");

        assertThat(dto.isOnline()).isTrue();
        assertThat(dto.getLastSeen()).isNotNull();
        assertThat(dto.isShowOnlineStatus()).isFalse();
    }

    @Test
    void updateProfile_persistsShowOnlineStatus_whenProvided() {
        User user = userWith("me", true);
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(user));
        when(presenceService.isOnline("me")).thenReturn(false);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setShowOnlineStatus(false);

        userService.updateProfile("me", req);

        assertThat(user.isShowOnlineStatus()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_leavesShowOnlineStatusUntouched_whenNotProvided() {
        User user = userWith("me", true);
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(user));
        when(presenceService.isOnline("me")).thenReturn(false);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("New Name");
        // showOnlineStatus left null on purpose

        userService.updateProfile("me", req);

        assertThat(user.isShowOnlineStatus()).isTrue();
        assertThat(user.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    void updatePublicKey_persistsAndReturnsItOnOwnProfile() {
        User user = userWith("me", true);
        when(userRepository.findByUsername("me")).thenReturn(Optional.of(user));
        when(presenceService.isOnline("me")).thenReturn(false);

        UserDto dto = userService.updatePublicKey("me", "BASE64ECDHKEY==");

        assertThat(user.getPublicKey()).isEqualTo("BASE64ECDHKEY==");
        assertThat(dto.getPublicKey()).isEqualTo("BASE64ECDHKEY==");
        verify(userRepository).save(user);
    }
}
