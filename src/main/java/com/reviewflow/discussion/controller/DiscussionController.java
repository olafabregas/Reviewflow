package com.reviewflow.discussion.controller;

import com.reviewflow.discussion.dto.request.CreateDiscussionRequest;
import com.reviewflow.discussion.dto.request.CreatePostRequest;
import com.reviewflow.discussion.dto.request.EditPostRequest;
import com.reviewflow.discussion.dto.request.PinPostRequest;
import com.reviewflow.discussion.dto.response.CreateDiscussionResponse;
import com.reviewflow.discussion.dto.response.CreatePostResponse;
import com.reviewflow.discussion.dto.response.DiscussionDetailResponse;
import com.reviewflow.discussion.dto.response.DiscussionPostsPageResponse;
import com.reviewflow.discussion.dto.response.DiscussionSummaryResponse;
import com.reviewflow.discussion.dto.response.NotPostedPageResponse;
import com.reviewflow.discussion.service.DiscussionService;
import com.reviewflow.infrastructure.security.ReviewFlowUserDetails;
import com.reviewflow.shared.domain.UserRole;
import com.reviewflow.shared.exception.ApiResponse;
import com.reviewflow.shared.util.HashidService;
import com.reviewflow.shared.util.PaginationHeaders;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Discussions", description = "PRD-17 course discussions")
public class DiscussionController {

  private final DiscussionService discussionService;
  private final HashidService hashidService;

  @PostMapping("/courses/{courseId}/discussions")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<CreateDiscussionResponse>> createDiscussion(
      @PathVariable String courseId,
      @Valid @RequestBody CreateDiscussionRequest body,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long cid = hashidService.decodeOrThrow(courseId);
    CreateDiscussionResponse data =
        discussionService.createDiscussion(cid, user.getUserId(), user.getRole(), body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
  }

  @PatchMapping("/discussions/{discussionId}/publish")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> publishDiscussion(
      @PathVariable String discussionId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long did = hashidService.decodeOrThrow(discussionId);
    discussionService.publishDiscussion(did, user.getUserId(), user.getRole());
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  @GetMapping("/courses/{courseId}/discussions")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<Page<DiscussionSummaryResponse>>> listDiscussions(
      @PathVariable String courseId,
      @AuthenticationPrincipal ReviewFlowUserDetails user,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Long cid = hashidService.decodeOrThrow(courseId);
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    Page<DiscussionSummaryResponse> data =
        discussionService.listDiscussionsForCourse(
            cid, user.getUserId(), user.getRole(), pageable);
    HttpHeaders headers = PaginationHeaders.forPage(data);
    return ResponseEntity.ok().headers(headers).body(ApiResponse.ok(data));
  }

  @GetMapping("/discussions/{discussionId}")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<DiscussionDetailResponse>> getDiscussion(
      @PathVariable String discussionId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long did = hashidService.decodeOrThrow(discussionId);
    return ResponseEntity.ok(
        ApiResponse.ok(discussionService.getDiscussion(did, user.getUserId(), user.getRole())));
  }

  @DeleteMapping("/discussions/{discussionId}")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<Void> deleteDiscussion(
      @PathVariable String discussionId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long did = hashidService.decodeOrThrow(discussionId);
    discussionService.deleteDiscussion(did, user.getUserId(), user.getRole());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/discussions/{discussionId}/posts")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<CreatePostResponse>> createPost(
      @PathVariable String discussionId,
      @Valid @RequestBody CreatePostRequest body,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long did = hashidService.decodeOrThrow(discussionId);
    CreatePostResponse data =
        discussionService.createPost(did, user.getUserId(), user.getRole(), body);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
  }

  @GetMapping("/discussions/{discussionId}/posts")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<DiscussionPostsPageResponse>> getPosts(
      @PathVariable String discussionId,
      @RequestParam(required = false) String after,
      @RequestParam(required = false) Integer limit,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long did = hashidService.decodeOrThrow(discussionId);
    return ResponseEntity.ok(
        ApiResponse.ok(
            discussionService.getPosts(did, user.getUserId(), user.getRole(), after, limit)));
  }

  @PutMapping("/discussion-posts/{postId}")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> editPost(
      @PathVariable String postId,
      @Valid @RequestBody EditPostRequest body,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long pid = hashidService.decodeOrThrow(postId);
    discussionService.editPost(pid, user.getUserId(), user.getRole(), body);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  @DeleteMapping("/discussion-posts/{postId}")
  @PreAuthorize("hasAnyRole('STUDENT','INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<Void> deletePost(
      @PathVariable String postId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long pid = hashidService.decodeOrThrow(postId);
    discussionService.softDeletePost(pid, user.getUserId(), user.getRole());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/discussion-posts/{postId}/pin")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> pinPost(
      @PathVariable String postId,
      @Valid @RequestBody PinPostRequest body,
      @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long pid = hashidService.decodeOrThrow(postId);
    discussionService.pinPost(pid, user.getUserId(), user.getRole(), body);
    return ResponseEntity.ok(ApiResponse.ok(null));
  }

  @GetMapping("/discussions/{discussionId}/not-posted")
  @PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN','SYSTEM_ADMIN')")
  public ResponseEntity<ApiResponse<NotPostedPageResponse>> notPosted(
      @PathVariable String discussionId, @AuthenticationPrincipal ReviewFlowUserDetails user) {
    Long did = hashidService.decodeOrThrow(discussionId);
    return ResponseEntity.ok(
        ApiResponse.ok(discussionService.getNotPosted(did, user.getUserId(), user.getRole())));
  }
}
