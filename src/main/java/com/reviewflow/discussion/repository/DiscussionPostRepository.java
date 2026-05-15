package com.reviewflow.discussion.repository;

import com.reviewflow.shared.domain.DiscussionPost;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscussionPostRepository extends JpaRepository<DiscussionPost, Long> {

  @Query(
      """
      SELECT COUNT(dp) FROM DiscussionPost dp
      JOIN dp.discussion d
      WHERE d.id = :discussionId
        AND dp.author.id = :userId
        AND dp.parentPost IS NULL
        AND (d.isGraded = false OR dp.isDeleted = false)
      """)
  long countCountingInitialPosts(
      @Param("discussionId") Long discussionId, @Param("userId") Long userId);

  @EntityGraph(attributePaths = {"author"})
  List<DiscussionPost>
      findByDiscussion_IdAndIsPinnedTrueAndParentPostIsNullOrderByCreatedAtAscIdAsc(
          Long discussionId);

  @EntityGraph(attributePaths = {"author"})
  @Query(
      """
      SELECT dp FROM DiscussionPost dp
      WHERE dp.discussion.id = :discussionId
        AND dp.parentPost IS NULL
        AND dp.isPinned = false
        AND dp.id > :afterId
      ORDER BY dp.createdAt ASC, dp.id ASC
      """)
  List<DiscussionPost> findNonPinnedInitialPage(
      @Param("discussionId") Long discussionId, @Param("afterId") long afterId, Pageable pageable);

  @EntityGraph(attributePaths = {"author"})
  @Query(
      """
      SELECT dp FROM DiscussionPost dp
      WHERE dp.discussion.id = :discussionId
        AND dp.parentPost.id IN :parentIds
      ORDER BY dp.createdAt ASC, dp.id ASC
      """)
  List<DiscussionPost> findRepliesForParents(
      @Param("discussionId") Long discussionId, @Param("parentIds") Collection<Long> parentIds);

  @EntityGraph(attributePaths = {"author", "discussion"})
  @Query(
      """
      SELECT dp FROM DiscussionPost dp
      JOIN dp.discussion d
      WHERE d.id = :discussionId
        AND dp.author.id = :userId
        AND dp.parentPost IS NULL
        AND (d.isGraded = false OR dp.isDeleted = false)
      ORDER BY dp.createdAt DESC, dp.id DESC
      """)
  List<DiscussionPost> findCountingInitialsForAuthor(
      @Param("discussionId") Long discussionId, @Param("userId") Long userId, Pageable limitOne);

  @Query(
      """
      SELECT dp FROM DiscussionPost dp
      JOIN FETCH dp.discussion d
      JOIN FETCH d.course
      JOIN FETCH dp.author
      WHERE dp.id = :id
      """)
  Optional<DiscussionPost> findDetailedById(@Param("id") Long id);
}
