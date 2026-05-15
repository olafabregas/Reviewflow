package com.reviewflow.discussion.dto.response;

import java.util.List;

public record DiscussionPostsPageResponse(
    List<DiscussionPostResponse> pinnedPosts,
    List<DiscussionPostResponse> posts,
    boolean hasMore,
    String nextCursor,
    MyPostResponse myPost) {}
