package com.reviewflow.discussion.dto.response;

public record PostRequiredBody(boolean canPost, DiscussionPromptDto discussion, MyPostResponse myPost) {}
