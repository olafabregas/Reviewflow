package com.reviewflow.discussion.dto.response;

import java.util.List;

public record NotPostedPageResponse(int notPostedCount, List<NotPostedStudentResponse> students) {}
