package com.ems.server.repository;

import com.ems.server.entity.PowerRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PowerRecordRepository extends JpaRepository<PowerRecord, Long> {

    /** AI 가이드 매칭용. 동일 timestamp 가 여러 건이면 가장 최근 수신분(id 큰 순). */
    Optional<PowerRecord> findFirstByTimestampOrderByIdDesc(String timestamp);

    /** 과거 기록 사이드바용 — 최신순 N개. */
    List<PowerRecord> findAllByOrderByIdDesc(Pageable pageable);
}
