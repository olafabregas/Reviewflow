package com.reviewflow.discussion.service;

import com.reviewflow.assignment.repository.AssignmentRepository;
import com.reviewflow.course.repository.CourseEnrollmentRepository;
import com.reviewflow.course.repository.CourseInstructorRepository;
import com.reviewflow.course.repository.CourseRepository;
import com.reviewflow.discussion.dto.request.CreateDiscussionRequest;
import com.reviewflow.discussion.dto.request.CreatePostRequest;
import com.reviewflow.discussion.dto.request.EditPostRequest;
import com.reviewflow.discussion.dto.request.PinPostRequest;
import com.reviewflow.discussion.dto.response.CreateDiscussionResponse;
import com.reviewflow.discussion.dto.response.CreatePostResponse;
import com.reviewflow.discussion.dto.response.DiscussionDetailResponse;
import com.reviewflow.discussion.dto.response.DiscussionPostResponse;
import com.reviewflow.discussion.dto.response.DiscussionPostsPageResponse;
import com.reviewflow.discussion.dto.response.DiscussionPromptDto;
import com.reviewflow.discussion.dto.response.DiscussionReplyResponse;
import com.reviewflow.discussion.dto.response.DiscussionSummaryResponse;
import com.reviewflow.discussion.dto.response.MyPostResponse;
import com.reviewflow.discussion.dto.response.NotPostedPageResponse;
import com.reviewflow.discussion.dto.response.NotPostedStudentResponse;
import com.reviewflow.discussion.dto.response.PostRequiredBody;
import com.reviewflow.discussion.event.DiscussionPublishedEvent;
import com.reviewflow.discussion.event.DiscussionReplyEvent;
import com.reviewflow.discussion.exception.DiscussionException;
import com.reviewflow.discussion.repository.DiscussionPostRepository;
import com.reviewflow.discussion.repository.DiscussionRepository;
import com.reviewflow.discussion.repository.NotPostedStudentRow;
import com.reviewflow.shared.constant.CacheNames;
import com.reviewflow.shared.domain.Assignment;
import com.reviewflow.shared.domain.Course;
import com.reviewflow.shared.domain.Discussion;
import com.reviewflow.shared.domain.DiscussionPost;
import com.reviewflow.shared.domain.SubmissionType;
import com.reviewflow.shared.domain.User;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ResourceNotFoundException;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscussionService {

  private static final int DEFAULT_POST_LIMIT = 20;
  private static final int MAX_POST_LIMIT = 50;

  private final DiscussionRepository discussionRepository;
  private final DiscussionPostRepository discussionPostRepository;
  private final CourseRepository courseRepository;
  private final CourseEnrollmentRepository courseEnrollmentRepository;
  private final CourseInstructorRepository courseInstructorRepository;
  private final UserRepository userRepository;
  private final AssignmentRepository assignmentRepository;
  private final DiscussionParticipationService participationService;
  private final ApplicationEventPublisher eventPublisher;
  private final HashidService hashidService;
  private final CacheManager cacheManager;

  private void assertUserHasCourseAccess(Long courseId, Long userId, UserRole role) {
    if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
      return;
    }
    if (role == UserRole.INSTRUCTOR) {
      if (!courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId)) {
        throw new DiscussionException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Not an instructor for this course");
      }
      return;
    }
    if (!courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, userId)) {
      throw new DiscussionException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Not enrolled in this course");
    }
  }

  private void evictCourseDiscussionList(Long courseId) {
    var cache = cacheManager.getCache(CacheNames.CACHE_COURSE_DISCUSSIONS);
    if (cache == null) {
      return;
    }
    for (UserRole r : UserRole.values()) {
      cache.evict(courseId + ":" + r.name());
    }
  }

  @Cacheable(value = CacheNames.CACHE_COURSE_DISCUSSIONS, key = "#courseId + ':' + #role.name()")
  @Transactional(readOnly = true)
  public List<DiscussionSummaryResponse> listDiscussionsForCourse(
      Long courseId, Long userId, UserRole role) {
    assertUserHasCourseAccess(courseId, userId, role);
    return discussionRepository.findByCourseIdOrderByDueAtAsc(courseId).stream()
        .filter(d -> role != UserRole.STUDENT || Boolean.TRUE.equals(d.getIsPublished()))
        .map(this::toSummary)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<DiscussionSummaryResponse> listDiscussionsForCourse(
      Long courseId, Long userId, UserRole role, org.springframework.data.domain.Pageable pageable) {
    assertUserHasCourseAccess(courseId, userId, role);
    org.springframework.data.domain.Page<Discussion> page =
        discussionRepository.findByCourseIdOrderByDueAtAsc(courseId, pageable);
    java.util.List<DiscussionSummaryResponse> content =
        page.getContent().stream()
            .filter(d -> role != UserRole.STUDENT || Boolean.TRUE.equals(d.getIsPublished()))
            .map(this::toSummary)
            .collect(Collectors.toList());
    return new org.springframework.data.domain.PageImpl<>(
        content, pageable, page.getTotalElements());
  }

  @Transactional(readOnly = true)
  public DiscussionDetailResponse getDiscussion(Long discussionId, Long userId, UserRole role) {
    Discussion d = loadDiscussion(discussionId);
    assertCanAccessDiscussion(d, userId, role);
    if (!Boolean.TRUE.equals(d.getIsPublished()) && !canModerate(d.getCourse().getId(), userId, role)) {
      throw new DiscussionException(
          HttpStatus.NOT_FOUND, "DISCUSSION_NOT_PUBLISHED", "Discussion is not published");
    }
    return toDetail(d);
  }

  @Transactional
  public CreateDiscussionResponse createDiscussion(
      Long courseId, Long actorId, UserRole role, CreateDiscussionRequest req) {
    assertInstructorOrAdmin(courseId, actorId, role);
    User creator =
        userRepository.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("User", actorId));
    Course course =
        courseRepository.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Course", courseId));

    Assignment assignment = null;
    if (req.isGraded()) {
      if (req.getAssignmentId() == null || req.getAssignmentId().isBlank()) {
        throw new DiscussionException(
            HttpStatus.BAD_REQUEST, "VALIDATION", "assignmentId is required when isGraded is true");
      }
      Long aid = hashidService.decodeOrThrow(req.getAssignmentId());
      assignment =
          assignmentRepository
              .findById(aid)
              .orElseThrow(() -> new ResourceNotFoundException("Assignment", aid));
      if (!assignment.getCourse().getId().equals(courseId)) {
        throw new DiscussionException(HttpStatus.BAD_REQUEST, "VALIDATION", "Assignment not in this course");
      }
      if (assignment.getSubmissionType() != SubmissionType.INSTRUCTOR_GRADED) {
        throw new DiscussionException(
            HttpStatus.BAD_REQUEST, "VALIDATION", "Assignment must be INSTRUCTOR_GRADED for graded discussions");
      }
    }

    Instant now = Instant.now();
    Discussion d =
        Discussion.builder()
            .course(course)
            .assignment(assignment)
            .title(req.getTitle().trim())
            .prompt(req.getPrompt().trim())
            .dueAt(req.getDueAt())
            .requirePostBeforeReading(req.isRequirePostBeforeReading())
            .allowAnonymous(req.isAllowAnonymous())
            .isGraded(req.isGraded())
            .isPublished(false)
            .createdBy(creator)
            .createdAt(now)
            .updatedAt(now)
            .build();
    d = discussionRepository.save(d);
    evictCourseDiscussionList(courseId);
    return new CreateDiscussionResponse(
        hashidService.encode(d.getId()), d.getTitle(), Boolean.TRUE.equals(d.getIsPublished()));
  }

  @Transactional
  public void publishDiscussion(Long discussionId, Long actorId, UserRole role) {
    Discussion d = loadDiscussion(discussionId);
    assertInstructorOrAdmin(d.getCourse().getId(), actorId, role);
    if (Boolean.TRUE.equals(d.getIsPublished())) {
      return;
    }
    Instant now = Instant.now();
    d.setIsPublished(true);
    d.setPublishedAt(now);
    d.setUpdatedAt(now);
    discussionRepository.save(d);
    evictCourseDiscussionList(d.getCourse().getId());
    eventPublisher.publishEvent(
        new DiscussionPublishedEvent(
            d.getCourse().getId(),
            d.getCourse().getCode(),
            d.getId(),
            d.getTitle(),
            d.getDueAt()));
  }

  @Transactional
  public void deleteDiscussion(Long discussionId, Long actorId, UserRole role) {
    Discussion d = loadDiscussion(discussionId);
    assertInstructorOrAdmin(d.getCourse().getId(), actorId, role);
    Long courseId = d.getCourse().getId();
    discussionRepository.delete(d);
    evictCourseDiscussionList(courseId);
  }

  @Transactional(readOnly = true)
  public DiscussionPostsPageResponse getPosts(
      Long discussionId,
      Long userId,
      UserRole role,
      String afterHash,
      Integer limitParam) {
    Discussion d = loadDiscussion(discussionId);
    assertCanAccessDiscussion(d, userId, role);
    if (!Boolean.TRUE.equals(d.getIsPublished()) && !canModerate(d.getCourse().getId(), userId, role)) {
      throw new DiscussionException(
          HttpStatus.NOT_FOUND, "DISCUSSION_NOT_PUBLISHED", "Discussion is not published");
    }

    boolean student = role == UserRole.STUDENT;
    if (Boolean.TRUE.equals(d.getRequirePostBeforeReading())
        && student
        && !participationService.hasStudentPosted(discussionId, userId)) {
      PostRequiredBody body =
          new PostRequiredBody(
              true,
              new DiscussionPromptDto(d.getTitle(), d.getPrompt(), d.getDueAt()),
              null);
      throw new DiscussionException(
          HttpStatus.FORBIDDEN,
          "POST_REQUIRED_BEFORE_READING",
          "Post your own response to see classmates' posts.",
          body);
    }

    int limit = limitParam == null ? DEFAULT_POST_LIMIT : Math.min(Math.max(1, limitParam), MAX_POST_LIMIT);
    long afterId = afterHash == null || afterHash.isBlank() ? 0L : hashidService.decodeOrThrow(afterHash);

    List<DiscussionPost> pinnedEntities = List.of();
    if (afterHash == null || afterHash.isBlank()) {
      pinnedEntities =
          discussionPostRepository
              .findByDiscussion_IdAndIsPinnedTrueAndParentPostIsNullOrderByCreatedAtAscIdAsc(discussionId);
    }

    List<DiscussionPost> page =
        discussionPostRepository.findNonPinnedInitialPage(
            discussionId, afterId, PageRequest.of(0, limit));

    Set<Long> parentIds = new LinkedHashSet<>();
    for (DiscussionPost p : pinnedEntities) {
      parentIds.add(p.getId());
    }
    for (DiscussionPost p : page) {
      parentIds.add(p.getId());
    }

    List<DiscussionPost> replies =
        parentIds.isEmpty()
            ? List.of()
            : discussionPostRepository.findRepliesForParents(discussionId, parentIds);

    Map<Long, List<DiscussionPost>> repliesByParent = new LinkedHashMap<>();
    for (DiscussionPost r : replies) {
      Long pid = r.getParentPost().getId();
      repliesByParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
    }

    List<DiscussionPostResponse> pinnedWithReplies =
        pinnedEntities.stream()
            .map(
                p ->
                    toPostResponse(
                        p,
                        d,
                        userId,
                        role,
                        mapReplies(repliesByParent.getOrDefault(p.getId(), List.of()), d, userId, role)))
            .collect(Collectors.toList());

    List<DiscussionPostResponse> postsWithReplies =
        page.stream()
            .map(
                p ->
                    toPostResponse(
                        p,
                        d,
                        userId,
                        role,
                        mapReplies(repliesByParent.getOrDefault(p.getId(), List.of()), d, userId, role)))
            .collect(Collectors.toList());

    boolean hasMore = page.size() == limit;
    String nextCursor =
        hasMore && !page.isEmpty() ? hashidService.encode(page.get(page.size() - 1).getId()) : null;

    MyPostResponse myPost = buildMyPost(d, userId);

    return new DiscussionPostsPageResponse(pinnedWithReplies, postsWithReplies, hasMore, nextCursor, myPost);
  }

  private List<DiscussionReplyResponse> mapReplies(
      List<DiscussionPost> rel, Discussion d, Long viewerId, UserRole viewerRole) {
    return rel.stream().map(r -> toReplyResponse(r, d, viewerId, viewerRole)).collect(Collectors.toList());
  }

  private DiscussionPostResponse toPostResponse(
      DiscussionPost p, Discussion d, Long viewerId, UserRole viewerRole, List<DiscussionReplyResponse> replies) {
    User author = p.getAuthor();
    return new DiscussionPostResponse(
        hashidService.encode(p.getId()),
        Boolean.TRUE.equals(p.getIsDeleted()) ? null : p.getContent(),
        displayAuthorName(d, author, viewerId, viewerRole),
        author.getAvatarUrl(),
        author.getRole() == UserRole.INSTRUCTOR,
        Boolean.TRUE.equals(p.getIsPinned()),
        Optional.ofNullable(p.getWordCount()).orElse(0),
        Boolean.TRUE.equals(p.getIsDeleted()),
        p.getCreatedAt(),
        replies);
  }

  private DiscussionReplyResponse toReplyResponse(
      DiscussionPost r, Discussion d, Long viewerId, UserRole viewerRole) {
    User author = r.getAuthor();
    return new DiscussionReplyResponse(
        hashidService.encode(r.getId()),
        Boolean.TRUE.equals(r.getIsDeleted()) ? null : r.getContent(),
        displayAuthorName(d, author, viewerId, viewerRole),
        author.getAvatarUrl(),
        Optional.ofNullable(r.getWordCount()).orElse(0),
        Boolean.TRUE.equals(r.getIsDeleted()),
        r.getCreatedAt());
  }

  private String displayAuthorName(
      Discussion discussion, User author, Long viewerId, UserRole viewerRole) {
    if (!Boolean.TRUE.equals(discussion.getAllowAnonymous())) {
      return author.getFullNameOrEmail();
    }
    if (isStaffRole(viewerRole)) {
      return author.getFullNameOrEmail();
    }
    if (Objects.equals(viewerId, author.getId())) {
      return author.getFullNameOrEmail();
    }
    if (author.getRole() == UserRole.STUDENT) {
      return "Anonymous";
    }
    return author.getFullNameOrEmail();
  }

  private boolean isStaffRole(UserRole role) {
    return role == UserRole.INSTRUCTOR || role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN;
  }

  private static String emailSnippet(String content, int maxLen) {
    if (content == null) {
      return null;
    }
    String trimmed = content.strip();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() <= maxLen) {
      return trimmed;
    }
    return trimmed.substring(0, maxLen - 1) + "\u2026";
  }

  private MyPostResponse buildMyPost(Discussion d, Long userId) {
    List<DiscussionPost> mine =
        discussionPostRepository.findCountingInitialsForAuthor(
            d.getId(), userId, PageRequest.of(0, 1));
    if (mine.isEmpty()) {
      return null;
    }
    DiscussionPost p = mine.get(0);
    return new MyPostResponse(
        hashidService.encode(p.getId()),
        Boolean.TRUE.equals(p.getIsDeleted()) ? null : p.getContent(),
        p.getCreatedAt());
  }

  @Transactional
  public CreatePostResponse createPost(Long discussionId, Long authorId, UserRole role, CreatePostRequest req) {
    Discussion d = loadDiscussion(discussionId);
    assertCanAccessDiscussion(d, authorId, role);
    if (!Boolean.TRUE.equals(d.getIsPublished())) {
      throw new DiscussionException(
          HttpStatus.NOT_FOUND, "DISCUSSION_NOT_PUBLISHED", "Discussion is not published");
    }
    if (Instant.now().isAfter(d.getDueAt())) {
      throw new DiscussionException(HttpStatus.CONFLICT, "DISCUSSION_CLOSED", "Discussion is closed");
    }

    User author =
        userRepository.findById(authorId).orElseThrow(() -> new ResourceNotFoundException("User", authorId));

    final Long parentPostId =
        (req.getParentPostId() != null && !req.getParentPostId().isBlank())
            ? hashidService.decodeOrThrow(req.getParentPostId())
            : null;

    DiscussionPost resolvedParent = null;
    if (parentPostId == null) {
      if (discussionPostRepository.countCountingInitialPosts(discussionId, authorId) > 0) {
        throw new DiscussionException(HttpStatus.CONFLICT, "ALREADY_POSTED", "You already posted an initial response");
      }
    } else {
      resolvedParent =
          discussionPostRepository
              .findById(parentPostId)
              .orElseThrow(() -> new ResourceNotFoundException("DiscussionPost", parentPostId));
      if (!resolvedParent.getDiscussion().getId().equals(discussionId)) {
        throw new DiscussionException(HttpStatus.BAD_REQUEST, "VALIDATION", "Parent post not in this discussion");
      }
      if (resolvedParent.getParentPost() != null) {
        throw new DiscussionException(
            HttpStatus.BAD_REQUEST, "REPLY_DEPTH_EXCEEDED", "Replies may be only one level deep");
      }
    }

    Instant now = Instant.now();
    int wc = wordCount(req.getContent());
    DiscussionPost post =
        DiscussionPost.builder()
            .discussion(d)
            .parentPost(
                parentPostId == null ? null : discussionPostRepository.getReferenceById(parentPostId))
            .author(author)
            .content(req.getContent())
            .wordCount(wc)
            .isPinned(false)
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    post = discussionPostRepository.save(post);

    if (parentPostId == null) {
      participationService.evictParticipation(discussionId, authorId);
    }
    evictCourseDiscussionList(d.getCourse().getId());

    if (resolvedParent != null) {
      eventPublisher.publishEvent(
          new DiscussionReplyEvent(
              discussionId,
              resolvedParent.getAuthor().getId(),
              resolvedParent.getAuthor().getRole(),
              authorId,
              author.getRole(),
              author.getFullNameOrEmail(),
              d.getTitle(),
              emailSnippet(req.getContent(), 240)));
    }

    return new CreatePostResponse(
        hashidService.encode(post.getId()),
        post.getContent(),
        displayAuthorName(d, author, authorId, role),
        post.getCreatedAt(),
        post.getWordCount());
  }

  @Transactional
  public void editPost(Long postId, Long actorId, UserRole role, EditPostRequest req) {
    DiscussionPost p =
        discussionPostRepository
            .findDetailedById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("DiscussionPost", postId));
    Discussion d = p.getDiscussion();
    assertCanAccessDiscussion(d, actorId, role);
    if (!Objects.equals(p.getAuthor().getId(), actorId) && !canModerate(d.getCourse().getId(), actorId, role)) {
      throw new DiscussionException(HttpStatus.FORBIDDEN, "CANNOT_EDIT_OTHERS_POST", "Cannot edit another user's post");
    }
    if (Boolean.TRUE.equals(p.getIsDeleted())) {
      throw new DiscussionException(HttpStatus.BAD_REQUEST, "VALIDATION", "Cannot edit a deleted post");
    }
    Instant now = Instant.now();
    p.setContent(req.getContent());
    p.setWordCount(wordCount(req.getContent()));
    p.setUpdatedAt(now);
    discussionPostRepository.save(p);
    evictCourseDiscussionList(d.getCourse().getId());
    participationService.evictParticipation(d.getId(), p.getAuthor().getId());
  }

  @Transactional
  public void softDeletePost(Long postId, Long actorId, UserRole role) {
    DiscussionPost p =
        discussionPostRepository
            .findDetailedById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("DiscussionPost", postId));
    Discussion d = p.getDiscussion();
    boolean author = Objects.equals(p.getAuthor().getId(), actorId);
    if (!author && !canModerate(d.getCourse().getId(), actorId, role)) {
      throw new DiscussionException(HttpStatus.FORBIDDEN, "CANNOT_EDIT_OTHERS_POST", "Cannot delete this post");
    }
    if (Boolean.TRUE.equals(p.getIsDeleted())) {
      return;
    }
    p.setIsDeleted(true);
    p.setContent(null);
    p.setUpdatedAt(Instant.now());
    discussionPostRepository.save(p);
    evictCourseDiscussionList(d.getCourse().getId());
    if (p.getParentPost() == null && Boolean.TRUE.equals(d.getIsGraded())) {
      participationService.evictParticipation(d.getId(), p.getAuthor().getId());
    }
  }

  @Transactional
  public void pinPost(Long postId, Long actorId, UserRole role, PinPostRequest req) {
    DiscussionPost p =
        discussionPostRepository
            .findDetailedById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("DiscussionPost", postId));
    Discussion d = p.getDiscussion();
    assertInstructorOrAdmin(d.getCourse().getId(), actorId, role);
    if (p.getParentPost() != null) {
      throw new DiscussionException(HttpStatus.BAD_REQUEST, "VALIDATION", "Only initial posts can be pinned");
    }
    p.setIsPinned(Boolean.TRUE.equals(req.getPinned()));
    p.setUpdatedAt(Instant.now());
    discussionPostRepository.save(p);
    evictCourseDiscussionList(d.getCourse().getId());
  }

  @Transactional(readOnly = true)
  public NotPostedPageResponse getNotPosted(Long discussionId, Long instructorId, UserRole role) {
    Discussion d = loadDiscussion(discussionId);
    assertInstructorOrAdmin(d.getCourse().getId(), instructorId, role);
    List<NotPostedStudentRow> rows = discussionRepository.findNotPostedStudents(discussionId);
    List<NotPostedStudentResponse> students =
        rows.stream()
            .map(
                r ->
                    new NotPostedStudentResponse(
                        hashidService.encode(r.getUserId()),
                        r.getFirstName(),
                        r.getLastName(),
                        r.getEmail()))
            .collect(Collectors.toList());
    return new NotPostedPageResponse(students.size(), students);
  }

  private Discussion loadDiscussion(Long id) {
    return discussionRepository
        .findWithCourseById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Discussion", id));
  }

  private void assertCanAccessDiscussion(Discussion d, Long userId, UserRole role) {
    Long courseId = d.getCourse().getId();
    if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
      return;
    }
    if (role == UserRole.INSTRUCTOR) {
      if (!courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId)) {
        throw new DiscussionException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Not an instructor for this course");
      }
      return;
    }
    if (!courseEnrollmentRepository.existsByCourseIdAndUserId(courseId, userId)) {
      throw new DiscussionException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Not enrolled in this course");
    }
  }

  private void assertInstructorOrAdmin(Long courseId, Long userId, UserRole role) {
    if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
      return;
    }
    if (role != UserRole.INSTRUCTOR) {
      throw new DiscussionException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Instructor access required");
    }
    if (!courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId)) {
      throw new DiscussionException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Not an instructor for this course");
    }
  }

  private boolean canModerate(Long courseId, Long userId, UserRole role) {
    if (role == UserRole.ADMIN || role == UserRole.SYSTEM_ADMIN) {
      return true;
    }
    return role == UserRole.INSTRUCTOR
        && courseInstructorRepository.existsByCourseIdAndUserId(courseId, userId);
  }

  private DiscussionSummaryResponse toSummary(Discussion d) {
    return new DiscussionSummaryResponse(
        hashidService.encode(d.getId()),
        d.getTitle(),
        Boolean.TRUE.equals(d.getIsPublished()),
        d.getDueAt(),
        Boolean.TRUE.equals(d.getIsGraded()));
  }

  private DiscussionDetailResponse toDetail(Discussion d) {
    return new DiscussionDetailResponse(
        hashidService.encode(d.getId()),
        d.getTitle(),
        d.getPrompt(),
        d.getDueAt(),
        Boolean.TRUE.equals(d.getRequirePostBeforeReading()),
        Boolean.TRUE.equals(d.getAllowAnonymous()),
        Boolean.TRUE.equals(d.getIsGraded()),
        d.getAssignment() == null ? null : hashidService.encode(d.getAssignment().getId()),
        Boolean.TRUE.equals(d.getIsPublished()),
        d.getPublishedAt());
  }

  private static int wordCount(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }
}
