package com.reviewflow.discussion.repository;

/** Native-query projection for not-posted list. */
public interface NotPostedStudentRow {

  Long getUserId();

  String getFirstName();

  String getLastName();

  String getEmail();
}
