package com.example.messenger.security;

import com.example.messenger.model.User;
import com.example.messenger.repository.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        // enabled=false for banned users: rejected both at login (DaoAuthenticationProvider) and,
        // via JwtAuthFilter's isEnabled() check below, immediately for any already-issued token too.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), !user.isBanned(), true, true, true, Collections.emptyList());
    }
}
