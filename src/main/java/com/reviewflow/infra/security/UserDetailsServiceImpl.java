package com.reviewflow.infra.security;

import com.reviewflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public ReviewFlowUserDetails loadUserByUsername(String username)
      throws UsernameNotFoundException {
    return userRepository
        .findByEmail(username)
        .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
        .map(ReviewFlowUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
  }
}
