package com.ems.server.repository;

import com.ems.server.entity.AiGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiGuideRepository extends JpaRepository<AiGuide, Long> {
}
