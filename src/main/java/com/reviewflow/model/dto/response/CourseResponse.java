package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CourseResponse {

    Long id;
    String code;
    String name;
    String term;
    String description;
    Boolean isArchived;
    Long createdById;
}
