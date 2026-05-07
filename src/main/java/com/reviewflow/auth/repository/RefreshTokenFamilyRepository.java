package com.reviewflow.auth.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reviewflow.auth.entity.RefreshTokenFamily;

public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {

  List<RefreshTokenFamily> findByUser_Id(Long userId);

  List<RefreshTokenFamily> findByUser_IdAndRevokedAtIsNull(Long userId);
}
