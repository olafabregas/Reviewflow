package com.reviewflow.model.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CourseResponse {

    String id;
    String code;
    String name;
    String term;
    String description;
    Boolean isArchived;
    String createdById;
    Integer instructorCount;
    Integer enrollmentCount;
    Integer assignmentCount;
}
