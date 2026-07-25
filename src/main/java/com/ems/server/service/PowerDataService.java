package com.ems.server.service;

import com.ems.server.dto.AgentGuidePayloadDto;
import com.ems.server.dto.GuideContentDto;
import com.ems.server.dto.GuideViewDto;
import com.ems.server.dto.RecordPayloadDto;
import com.ems.server.dto.RecordSummaryDto;
import com.ems.server.dto.RiskPointDto;
import com.ems.server.entity.AiGuide;
import com.ems.server.entity.PowerRecord;
import com.ems.server.repository.AiGuideRepository;
import com.ems.server.repository.PowerRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 분석 서버 수신 + 과거 기록 조회 + 가이드 상세 조회.
 *
 * 가이드 정책:
 *   - 분석 서버는 15분마다 record 를 보내고, 해당 시점들의 해설을 담은
 *     가이드(guide.risk_points[])를 전송한다.
 *   - 백엔드는 guide.risk_points[] 를 사용한다. 각 risk_point.timestamp 로
 *     PowerRecord 를 찾아 매칭되는 시점에 AiGuide row 를 저장한다.
 *   - risk_points 에는 정상(NORMAL) 시점도 포함된다 — 정상/위험 구분은
 *     각 row 의 riskLevel 필드(NORMAL / WATCH / WARN / CRITICAL)로 한다.
 *   - 종합 정보(summary, operator_message 등)는 저장하지 않고 시점별 해설만 저장한다.
 *   - 매칭 실패(record 미도착 등)나 timestamp 누락은 skip 카운트로 집계한다.
 */
@Service
public class PowerDataService {

    @Autowired
    private PowerRecordRepository recordRepo;

    @Autowired
    private AiGuideRepository guideRepo;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // =======================================================================
    // 수신 (record)
    // =======================================================================
    @Transactional
    public PowerRecord saveRecord(RecordPayloadDto dto) {
        PowerRecord entity = toRecordEntity(dto);
        PowerRecord saved = recordRepo.save(entity);

        // WebSocket 송출 시 DB id 포함
        ObjectNode out = objectMapper.valueToTree(dto);
        out.put("id", saved.getId());
        messagingTemplate.convertAndSend("/topic/alerts", out);
        return saved;
    }

    /**
     * record + (있으면) 해당 시점의 가이드(risk_point)를 한 트랜잭션에 저장하고,
     * WebSocket 으로 한 페이로드에 묶어 송출.
     *
     * 데모용: agent_guide.json 의 risk_points 7건을 timestamp 매칭하여
     * 그 시점의 record 가 도착할 때 함께 처리한다.
     *
     * → 프론트는 record 받자마자 그 시점이 위험인지 즉시 안다.
     *   사이드바 카드를 처음부터 정확한 색/상태로 그릴 수 있어 새로고침 불필요.
     */
    @Transactional
    public PowerRecord saveRecordWithGuide(RecordPayloadDto dto,
                                            RiskPointDto rp,
                                            String llmModel,
                                            String createdAt) {
        PowerRecord entity = toRecordEntity(dto);
        PowerRecord saved = recordRepo.save(entity);

        if (rp != null) {
            AiGuide g = new AiGuide();
            g.setRecord(saved);
            g.setRiskLevel(rp.getRiskLevel());
            g.setReason(rp.getReason());
            g.setEvidence(rp.getEvidence());
            g.setAction(rp.getAction());
            g.setLlmModel(llmModel);
            g.setCreatedAt(createdAt);
            guideRepo.save(g);
        }

        // record + 가이드를 한 페이로드로 묶어 송출
        ObjectNode out = objectMapper.valueToTree(dto);
        out.put("id", saved.getId());
        if (rp != null) {
            ObjectNode gNode = objectMapper.createObjectNode();
            gNode.put("riskLevel", rp.getRiskLevel());
            gNode.put("reason", rp.getReason());
            gNode.put("evidence", rp.getEvidence());
            gNode.put("action", rp.getAction());
            out.set("guide", gNode);
        }
        messagingTemplate.convertAndSend("/topic/alerts", out);
        return saved;
    }

    private PowerRecord toRecordEntity(RecordPayloadDto dto) {
        PowerRecord pr = new PowerRecord();
        pr.setTimestamp(dto.getTimestamp());
        pr.setCurrentPower(dto.getCurrentPower());
        pr.setPredictedNext15min(dto.getPredictedNext15min());
        pr.setActualNext15min(dto.getActualNext15min());
        pr.setErrorValue(dto.getError());
        pr.setAbsError(dto.getAbsError());

        List<Double> hourly = dto.getPredictedNext1hour();
        if (hourly != null) {
            if (hourly.size() > 0) pr.setPredicted15(hourly.get(0));
            if (hourly.size() > 1) pr.setPredicted30(hourly.get(1));
            if (hourly.size() > 2) pr.setPredicted45(hourly.get(2));
            if (hourly.size() > 3) pr.setPredicted60(hourly.get(3));
        }

        if (dto.getContext() != null) {
            pr.setTemp(dto.getContext().getTemp());
            pr.setHumidity(dto.getContext().getHumidity());
            pr.setProduction(dto.getContext().getProduction());
            pr.setWorkers(dto.getContext().getWorkers());
            pr.setQuarter(dto.getContext().getQuarter());
            pr.setIsWeekend(dto.getContext().getIsWeekend());
            pr.setIsHoliday(dto.getContext().getIsHoliday());
        }

        if (dto.getRisk() != null) {
            pr.setRiskLevel(dto.getRisk().getLevel());
            pr.setRiskReason(dto.getRisk().getReason());
            pr.setRatioToPeak(dto.getRisk().getRatioToPeak());
            pr.setBillPeakBaseline(dto.getRisk().getBillPeakBaseline());
            pr.setAssessLevel(dto.getRisk().getAssessLevel());
            pr.setTrigger(dto.getRisk().getTrigger());
        }

        pr.setAnomaly(dto.getAnomaly());
        pr.setStatus(dto.getStatus());
        return pr;
    }

    // =======================================================================
    // 수신 (agent_guide) — risk_points 풀어서 시점별로 저장
    // =======================================================================
    @Transactional
    public GuideSaveResult saveGuide(AgentGuidePayloadDto dto) {
        GuideContentDto content = dto.getGuide();
        if (content == null || content.getRiskPoints() == null || content.getRiskPoints().isEmpty()) {
            // 위험 시점 0건 — 모든 시점이 정상이라는 의미
            return new GuideSaveResult(0, 0, new ArrayList<>());
        }

        int matched = 0;
        int skipped = 0;
        List<Long> matchedIds = new ArrayList<>();

        for (RiskPointDto rp : content.getRiskPoints()) {
            if (rp.getTimestamp() == null) {
                skipped++;
                continue;
            }

            Optional<PowerRecord> matchedOpt =
                    recordRepo.findFirstByTimestampOrderByIdDesc(rp.getTimestamp());

            if (matchedOpt.isEmpty()) {
                skipped++;
                continue;
            }
            PowerRecord record = matchedOpt.get();

            // 중복 저장 방지 (같은 record 에 대해 가이드가 이미 있으면 덮어쓰기)
            AiGuide guide = guideRepo.findById(record.getId()).orElseGet(AiGuide::new);
            guide.setRecord(record);
            guide.setRiskLevel(rp.getRiskLevel());
            guide.setReason(rp.getReason());
            guide.setEvidence(rp.getEvidence());
            guide.setLlmModel(dto.getLlmModel());
            guide.setCreatedAt(dto.getCreatedAt());

            AiGuide saved = guideRepo.save(guide);
            matched++;
            matchedIds.add(saved.getId());
        }

        // WebSocket 으로 가이드 갱신 알림 — *트랜잭션 commit 후* 송출
        // (commit 전에 보내면 프론트가 fetch 했을 때 DB 에 아직 ai_guide 가 안 들어가 있음)
        final ObjectNode notify = objectMapper.createObjectNode();
        notify.put("type", "guide_updated");
        notify.put("matched", matched);
        notify.put("skipped", skipped);
        notify.set("record_ids", objectMapper.valueToTree(matchedIds));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend("/topic/alerts", notify);
                }
            });
        } else {
            // 트랜잭션 밖에서 호출됐을 때를 위한 fallback
            messagingTemplate.convertAndSend("/topic/alerts", notify);
        }

        return new GuideSaveResult(matched, skipped, matchedIds);
    }

    /** saveGuide 결과 요약. */
    public static class GuideSaveResult {
        public final int matched;
        public final int skipped;
        public final List<Long> matchedIds;

        public GuideSaveResult(int matched, int skipped, List<Long> matchedIds) {
            this.matched = matched;
            this.skipped = skipped;
            this.matchedIds = matchedIds;
        }
    }

    // =======================================================================
    // 조회 — 과거 기록 사이드바
    // =======================================================================
    @Transactional(readOnly = true)
    public List<RecordSummaryDto> getRecentRecords(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<PowerRecord> records = recordRepo.findAllByOrderByIdDesc(PageRequest.of(0, safeLimit));

        // N+1 방지 — 모든 record id 모아서 가이드를 한 번에 조회
        List<Long> ids = records.stream().map(PowerRecord::getId).collect(Collectors.toList());
        Map<Long, com.ems.server.entity.AiGuide> guidesById = guideRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(com.ems.server.entity.AiGuide::getId, g -> g));

        return records.stream()
                .map(r -> {
                    String recordLvl = r.getRiskLevel();
                    String finalLvl = "NORMAL";
                    boolean hasGuide = false;

                    com.ems.server.entity.AiGuide g = guidesById.get(r.getId());
                    if (g != null) {
                        hasGuide = true;
                        if (g.getRiskLevel() != null && !g.getRiskLevel().isBlank()) {
                            finalLvl = g.getRiskLevel();
                        }
                    }

                    RecordSummaryDto dto = new RecordSummaryDto();
                    dto.setId(r.getId());
                    dto.setTimestamp(r.getTimestamp());
                    dto.setCurrentPower(r.getCurrentPower());
                    // context (모달용)
                    dto.setTemp(r.getTemp());
                    dto.setHumidity(r.getHumidity());
                    dto.setProduction(r.getProduction());
                    dto.setWorkers(r.getWorkers());
                    dto.setQuarter(r.getQuarter());
                    dto.setIsWeekend(r.getIsWeekend());
                    dto.setIsHoliday(r.getIsHoliday());
                    // 위험도
                    dto.setRiskLevel(finalLvl);
                    dto.setRecordRiskLevel(recordLvl);
                    dto.setHasGuide(hasGuide);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // =======================================================================
    // 조회 — 가이드 상세 (id 로 조회, 해당 시점 해설이 없으면 empty → 404)
    // =======================================================================
    @Transactional(readOnly = true)
    public Optional<GuideViewDto> getGuide(Long id) {
        return guideRepo.findById(id).map(this::toGuideViewDto);
    }

    private GuideViewDto toGuideViewDto(AiGuide g) {
        GuideViewDto v = new GuideViewDto();
        v.setId(g.getId());
        v.setRiskLevel(g.getRiskLevel());
        v.setReason(g.getReason());
        v.setEvidence(g.getEvidence());
        v.setAction(g.getAction());
        v.setLlmModel(g.getLlmModel());
        v.setCreatedAt(g.getCreatedAt());
        if (g.getRecord() != null) {
            v.setRecordTimestamp(g.getRecord().getTimestamp());
        }
        return v;
    }
}
