package com.ems.server.controller;

import com.ems.server.dto.AgentGuidePayloadDto;
import com.ems.server.dto.GuideViewDto;
import com.ems.server.dto.RecordPayloadDto;
import com.ems.server.dto.RecordSummaryDto;
import com.ems.server.entity.PowerRecord;
import com.ems.server.service.PowerDataService;
import com.ems.server.service.PowerDataService.GuideSaveResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 분석 서버 수신 + 프론트 조회 통합 컨트롤러.
 *
 * 개발 중 프론트를 별도 포트에서 띄웠을 때 호출 가능하도록 @CrossOrigin 으로 CORS 허용.
 * (현재는 백엔드가 static 으로 직접 서빙하므로, 향후 정리 대상.)
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class PowerDataController {

    @Autowired
    private PowerDataService service;

    @Autowired
    private ObjectMapper objectMapper;

    // =======================================================================
    // 분석 서버 → 백엔드 (수신)
    // =======================================================================
    @PostMapping("/from-analyst")
    public ResponseEntity<Map<String, Object>> receive(@RequestBody JsonNode root) {
        String type = root.hasNonNull("type") ? root.get("type").asText("") : "";

        switch (type) {
            case "record":       return handleRecord(root);
            case "agent_guide":  return handleGuide(root);
            default:
                Map<String, Object> body = new HashMap<>();
                body.put("success", false);
                body.put("message", "알 수 없는 type: \"" + type + "\" (record / agent_guide 만 지원)");
                return ResponseEntity.badRequest().body(body);
        }
    }

    private ResponseEntity<Map<String, Object>> handleRecord(JsonNode root) {
        Map<String, Object> body = new HashMap<>();
        try {
            RecordPayloadDto dto = objectMapper.treeToValue(root, RecordPayloadDto.class);

            System.out.println("=========================================");
            System.out.println("[record 수신] timestamp=" + dto.getTimestamp()
                    + ", current_power=" + dto.getCurrentPower());

            PowerRecord saved = service.saveRecord(dto);

            System.out.println("[DB 저장] power_record id=" + saved.getId());
            System.out.println("[WS 송출] /topic/alerts (record)");
            System.out.println("=========================================");

            body.put("success", true);
            body.put("recordId", saved.getId());
            body.put("timestamp", saved.getTimestamp());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            System.err.println("[에러] record 처리 실패: " + e.getMessage());
            e.printStackTrace();
            body.put("success", false);
            body.put("message", "record 처리 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(body);
        }
    }

    private ResponseEntity<Map<String, Object>> handleGuide(JsonNode root) {
        Map<String, Object> body = new HashMap<>();
        try {
            AgentGuidePayloadDto dto = objectMapper.treeToValue(root, AgentGuidePayloadDto.class);

            int incomingRiskPoints = (dto.getGuide() != null && dto.getGuide().getRiskPoints() != null)
                    ? dto.getGuide().getRiskPoints().size() : 0;

            System.out.println("=========================================");
            System.out.println("[agent_guide 수신] risk_points=" + incomingRiskPoints
                    + ", llm=" + dto.getLlmModel());

            GuideSaveResult result = service.saveGuide(dto);

            System.out.println("[DB 저장] 매칭=" + result.matched + ", skip=" + result.skipped
                    + ", record_ids=" + result.matchedIds);
            System.out.println("[WS 송출] /topic/alerts (guide_updated)");
            System.out.println("=========================================");

            body.put("success", true);
            body.put("matched", result.matched);
            body.put("skipped", result.skipped);
            body.put("matchedRecordIds", result.matchedIds);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            System.err.println("[에러] guide 처리 실패: " + e.getMessage());
            e.printStackTrace();
            body.put("success", false);
            body.put("message", "guide 처리 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(body);
        }
    }

    // =======================================================================
    // 프론트 → 백엔드 (조회)
    // =======================================================================

    /** 과거 기록 사이드바: 최신순 N개 요약 정보 반환. */
    @GetMapping("/records")
    public List<RecordSummaryDto> listRecords(@RequestParam(defaultValue = "100") int limit) {
        return service.getRecentRecords(limit);
    }

    /** 가이드 팝업: 특정 id 의 가이드 상세 반환. */
    @GetMapping("/guides/{id}")
    public ResponseEntity<GuideViewDto> getGuide(@PathVariable Long id) {
        return service.getGuide(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
