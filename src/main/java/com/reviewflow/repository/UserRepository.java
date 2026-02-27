package com.reviewflow.repository;

import com.reviewflow.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    long countByRole(com.reviewflow.model.entity.UserRole role);
}
