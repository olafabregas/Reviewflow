package com.reviewflow.service;

import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.response.UserDetailResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final AdminStatsService adminStatsService;
    private final HashidService hashidService;

    public Page<User> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    public Page<User> listUsersFiltered(UserRole role, Boolean isActive, String search, Pageable pageable) {
        // Validate search length
        if (search != null && search.trim().length() > 0 && search.trim().length() < 2) {
            throw new ValidationException("Search term must be at least 2 characters", "VALIDATION_ERROR");
        }
        
        // Apply filters based on what's provided
        if (search != null && !search.trim().isEmpty()) {
            String searchTerm = search.trim();
            if (role != null && isActive != null) {
                return userRepository.searchUsersByRoleAndActive(searchTerm, role, isActive, pageable);
            } else if (role != null) {
                return userRepository.searchUsersByRole(searchTerm, role, pageable);
            } else if (isActive != null) {
                return userRepository.searchUsersByActive(searchTerm, isActive, pageable);
            } else {
                return userRepository.searchUsers(searchTerm, pageable);
            }
        } else {
            if (role != null && isActive != null) {
                return userRepository.findByRoleAndIsActive(role, isActive, pageable);
            } else if (role != null) {
                return userRepository.findByRole(role, pageable);
            } else if (isActive != null) {
                return userRepository.findByIsActive(isActive, pageable);
            } else {
                return userRepository.findAll(pageable);
            }
        }
    }
    
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
    
    public UserDetailResponse getUserByIdWithCounts(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        // Calculate courseCount: enrollments + instructor assignments
        long enrollmentCount = courseEnrollmentRepository.countByUser_Id(id);
        long instructorCount = courseInstructorRepository.countByUser_Id(id);
        long courseCount = enrollmentCount + instructorCount;
        
        // Calculate teamCount
        long teamCount = teamMemberRepository.countByUser_Id(id);
        
        return UserDetailResponse.builder()
                .id(hashidService.encode(user.getId()))
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .courseCount(courseCount)
                .teamCount(teamCount)
                .build();
    }

    @Transactional
    public User createUser(String email, String password, String firstName, String lastName, UserRole role) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException("User with email already exists: " + email, "EMAIL_EXISTS");
        }
        
        // Validate password length
        if (password == null || password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters", "VALIDATION_ERROR");
        }
        
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user = userRepository.save(user);
        adminStatsService.evictStats();
        return user;
    }

    @Transactional
    public User updateUser(Long id, String firstName, String lastName, UserRole role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (role != null) user.setRole(role);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(Long id, Long currentUserId) {
        // Prevent deactivating own account
        if (id.equals(currentUserId)) {
            throw new BusinessRuleException("Cannot deactivate your own account", "CANNOT_DEACTIVATE_SELF");
        }
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        // Check if already inactive
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new BusinessRuleException("User is already deactivated", "ALREADY_INACTIVE");
        }
        
        user.setIsActive(false);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        adminStatsService.evictStats();
        // TODO: Revoke all active refresh tokens
    }
    
    @Transactional
    public void reactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        
        // Check if already active
        if (Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessRuleException("User is already active", "ALREADY_ACTIVE");
        }
        
        user.setIsActive(true);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        adminStatsService.evictStats();
    }
}
