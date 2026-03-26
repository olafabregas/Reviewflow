package com.reviewflow.repository;

import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query("SELECT u.emailNotificationsEnabled FROM User u WHERE u.email = :email")
    Optional<Boolean> findEmailPreferenceByEmail(@Param("email") String email);

    long countByRole(UserRole role);

    Page<User> findByRole(UserRole role, Pageable pageable);

    Page<User> findByIsActive(Boolean isActive, Pageable pageable);

    Page<User> findByRoleAndIsActive(UserRole role, Boolean isActive, Pageable pageable);

    @Query("SELECT u FROM User u WHERE "
            + "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u WHERE "
            + "u.role = :role AND "
            + "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsersByRole(@Param("search") String search, @Param("role") UserRole role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE "
            + "u.isActive = :isActive AND "
            + "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsersByActive(@Param("search") String search, @Param("isActive") Boolean isActive, Pageable pageable);

    @Query("SELECT u FROM User u WHERE "
            + "u.role = :role AND u.isActive = :isActive AND "
            + "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsersByRoleAndActive(@Param("search") String search, @Param("role") UserRole role, @Param("isActive") Boolean isActive, Pageable pageable);
}
