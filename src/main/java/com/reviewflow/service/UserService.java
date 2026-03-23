package com.reviewflow.service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.reviewflow.exception.AvatarNotFoundException;
import com.reviewflow.exception.AvatarUploadFailedException;
import com.reviewflow.exception.BusinessRuleException;
import com.reviewflow.exception.DuplicateResourceException;
import com.reviewflow.exception.ResourceNotFoundException;
import com.reviewflow.exception.ValidationException;
import com.reviewflow.model.dto.response.AuthUserResponse;
import com.reviewflow.model.dto.response.UserDetailResponse;
import com.reviewflow.model.entity.User;
import com.reviewflow.model.entity.UserRole;
import com.reviewflow.repository.CourseEnrollmentRepository;
import com.reviewflow.repository.CourseInstructorRepository;
import com.reviewflow.repository.TeamMemberRepository;
import com.reviewflow.repository.UserRepository;
import com.reviewflow.storage.StorageService;
import com.reviewflow.util.S3KeyBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final FileSecurityValidator fileSecurityValidator;
    private final EXIFStripperService exifStripperService;
    private final StorageService storageService;
    private final AuditService auditService;
    private final AdminStatsService adminStatsService;
    private final HashidService hashidService;

    @Value("${app.avatar.max-size-bytes}")
    private long avatarMaxSizeBytes;

    @Value("${app.avatar.allowed-extensions}")
    private String avatarAllowedExtensions;

    @Value("${app.avatar.allowed-mime-types}")
    private String avatarAllowedMimeTypes;

    @Value("${aws.s3.bucket:${app.s3.bucket}}")
    private String s3Bucket;

    @Value("${aws.region:${app.s3.region}}")
    private String s3Region;

    @Value("${aws.s3.public-base-url:${app.s3.public-base-url:}}")
    private String s3PublicBaseUrl;

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
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .courseCount(courseCount)
                .teamCount(teamCount)
                .build();
    }

    @Transactional
    public AuthUserResponse uploadAvatar(Long userId, MultipartFile file, String ipAddress) {
        User user = getUserById(userId);

        ValidationConfig config = new ValidationConfig(
                parseCsvSet(avatarAllowedExtensions),
                parseCsvSet(avatarAllowedMimeTypes),
                avatarMaxSizeBytes
        );

        try {
            fileSecurityValidator.validateWithConfig(file, config);
        } catch (java.io.IOException ex) {
            throw new AvatarUploadFailedException("Failed to inspect avatar file", ex);
        }

        String extension = extractExtension(file.getOriginalFilename());
        String format = resolveImageFormat(extension);
        String mimeType = resolveImageMimeType(extension);
        byte[] strippedBytes = exifStripperService.strip(file, format);

        String key = buildAvatarKey(userId, extension);
        try {
            storageService.store(key, new ByteArrayInputStream(strippedBytes), strippedBytes.length, mimeType);
        } catch (RuntimeException ex) {
            throw new AvatarUploadFailedException("Failed to upload avatar", ex);
        }

        String avatarUrl = buildAvatarUrl(key);
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.log(userId, "AVATAR_UPLOADED", "User", userId, "avatarKey=" + key, ipAddress);

        return toAuthUserResponse(user);
    }

    @Transactional
    public AuthUserResponse deleteAvatar(Long userId, String ipAddress) {
        User user = getUserById(userId);
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            throw new AvatarNotFoundException("Avatar not found for user");
        }

        String previousAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        String key = deriveAvatarKey(userId, previousAvatarUrl);
        try {
            storageService.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Avatar delete from storage failed for user {} and key {}", userId, key, ex);
        }

        auditService.log(userId, "AVATAR_DELETED", "User", userId, "avatarKey=" + key, ipAddress);
        return toAuthUserResponse(user);
    }

    @Transactional
    public AuthUserResponse adminDeleteAvatar(Long targetUserId, Long adminUserId, String ipAddress) {
        User user = getUserById(targetUserId);
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            throw new AvatarNotFoundException("Avatar not found for user");
        }

        String previousAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(null);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        String key = deriveAvatarKey(targetUserId, previousAvatarUrl);
        try {
            storageService.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Admin avatar delete from storage failed for user {} and key {}", targetUserId, key, ex);
        }

        auditService.log(adminUserId, "ADMIN_AVATAR_REMOVED", "User", targetUserId, "avatarKey=" + key, ipAddress);
        return toAuthUserResponse(user);
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
        if (firstName != null) {
            user.setFirstName(firstName);
        }
        if (lastName != null) {
            user.setLastName(lastName);
        }
        if (role != null) {
            user.setRole(role);
        }
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

    private AuthUserResponse toAuthUserResponse(User user) {
        return AuthUserResponse.builder()
                .userId(hashidService.encode(user.getId()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .isActive(user.getIsActive())
                .role(user.getRole())
                .build();
    }

    private Set<String> parseCsvSet(String csv) {
        return Arrays.stream(csv.split("\\s*,\\s*"))
                .filter(value -> !value.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String resolveImageFormat(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" ->
                "jpeg";
            case "png" ->
                "png";
            case "webp" ->
                "webp";
            default ->
                throw new ValidationException("Unsupported avatar extension", "AVATAR_INVALID_TYPE");
        };
    }

    private String resolveImageMimeType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" ->
                "image/jpeg";
            case "png" ->
                "image/png";
            case "webp" ->
                "image/webp";
            default ->
                throw new ValidationException("Unsupported avatar extension", "AVATAR_INVALID_TYPE");
        };
    }

    private String buildAvatarKey(Long userId, String extension) {
        return S3KeyBuilder.avatarKey(hashidService.encode(userId), extension);
    }

    private String buildAvatarUrl(String key) {
        String baseUrl = (s3PublicBaseUrl != null && !s3PublicBaseUrl.isBlank())
                ? s3PublicBaseUrl
                : "https://" + s3Bucket + ".s3." + s3Region + ".amazonaws.com";
        return baseUrl + "/" + key + "?v=" + Instant.now().toEpochMilli();
    }

    private String deriveAvatarKey(Long userId, String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return buildAvatarKey(userId, "jpg");
        }

        String withoutQuery = avatarUrl.split("\\?")[0];
        int avatarsIndex = withoutQuery.indexOf("/avatars/");
        if (avatarsIndex >= 0) {
            return withoutQuery.substring(avatarsIndex + 1);
        }
        return S3KeyBuilder.avatarKey(hashidService.encode(userId), "jpg");
    }
}
