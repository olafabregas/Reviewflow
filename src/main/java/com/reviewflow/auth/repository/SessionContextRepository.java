package com.reviewflow.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reviewflow.auth.entity.SessionContext;

public interface SessionContextRepository extends JpaRepository<SessionContext, Long> {

  Optional<SessionContext> findByFamily_Id(UUID familyId);

  List<SessionContext> findByFamily_User_IdOrderByCreatedAtContext(Long userId);

  void deleteByFamily_Id(UUID familyId);
}
