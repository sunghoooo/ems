package com.ems.server.service;

import com.ems.server.dto.AgentGuidePayloadDto;
import com.ems.server.dto.RecordPayloadDto;
import com.ems.server.dto.RiskPointDto;
import com.ems.server.repository.AiGuideRepository;
import com.ems.server.repository.PowerRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 데모 자동 흘리기 서비스.
 *
 * 데모 전용 정책 — 가이드를 record 와 *함께* 흘린다:
 *   (실제 수신 경로에서는 record 와 가이드가 분리 도착하지만,
 *    데모에서는 화면이 매끄럽게 그려지도록 묶어서 처리한다.)
 *   1) 시작 시 agent_guide.json 의 risk_points(정상 포함 전 시점)를
 *      timestamp 로 매핑 (Map<String, RiskPoint>)
 *   2) 매 record 처리할 때 그 timestamp 가 매핑에 있으면 ai_guide row 같이 저장
 *   3) WebSocket 송출 시 record 페이로드에 guide 정보 같이 포함
 *
 * → 프론트는 record 받자마자 그 시점의 위험 여부를 알 수 있어,
 *   사이드바 카드를 처음부터 정확한 상태로 그린다. 새로고침/별도 송출 불필요.
 */
@Service
public class DemoService {

    @Autowired
    private PowerDataService powerDataService;

    @Autowired
    private PowerRecordRepository recordRepo;

    @Autowired
    private AiGuideRepository guideRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private volatile int totalScheduled = 0;
    private volatile int completed = 0;

    public synchronized DemoStatus start(int intervalSeconds, int maxRecords) throws Exception {
        stop();
        clearDb();

        // 데이터 로드
        List<RecordPayloadDto> records = loadRecords();
        AgentGuidePayloadDto guide = loadGuide();

        // risk_points 를 timestamp 로 매핑 — 매 record 처리 시 빠르게 lookup
        final Map<String, RiskPointDto> guideMap = new HashMap<>();
        if (guide != null && guide.getGuide() != null && guide.getGuide().getRiskPoints() != null) {
            for (RiskPointDto rp : guide.getGuide().getRiskPoints()) {
                if (rp.getTimestamp() != null) {
                    guideMap.put(rp.getTimestamp(), rp);
                }
            }
        }
        final String llmModel = guide != null ? guide.getLlmModel() : null;
        final String createdAt = guide != null ? guide.getCreatedAt() : null;

        int total = Math.min(records.size(), Math.max(1, maxRecords));
        int interval = Math.max(1, intervalSeconds);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "demo-scheduler");
            t.setDaemon(true);
            return t;
        });
        running = true;
        totalScheduled = total;
        completed = 0;

        System.out.println("=========================================");
        System.out.println("[데모 시작] records=" + total + ", interval=" + interval + "s, risk_points=" + guideMap.size());
        System.out.println("=========================================");

        for (int i = 0; i < total; i++) {
            final RecordPayloadDto dto = records.get(i);
            final int index = i;
            scheduler.schedule(() -> {
                try {
                    RiskPointDto rp = guideMap.get(dto.getTimestamp());
                    powerDataService.saveRecordWithGuide(dto, rp, llmModel, createdAt);

                    completed++;
                    String tag = (rp != null) ? "★" + rp.getRiskLevel() : "정상";
                    System.out.println("[데모] record " + (index + 1) + "/" + total
                            + " 전송 (" + dto.getTimestamp() + ") [" + tag + "]");

                    if (index + 1 == total) {
                        System.out.println("=========================================");
                        System.out.println("[데모 완료]");
                        System.out.println("=========================================");
                        running = false;
                    }
                } catch (Exception e) {
                    System.err.println("[데모] record " + (index + 1) + " 실패: " + e.getMessage());
                }
            }, (long) (i + 1) * interval, TimeUnit.SECONDS);
        }

        return status();
    }

    public synchronized void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        running = false;
    }

    public DemoStatus status() {
        return new DemoStatus(running, totalScheduled, completed);
    }

    @Transactional
    public void clearDb() {
        guideRepo.deleteAllInBatch();
        recordRepo.deleteAllInBatch();
    }

    // -----------------------------------------------------------------------
    // JSON 로드
    // -----------------------------------------------------------------------
    private List<RecordPayloadDto> loadRecords() throws Exception {
        ClassPathResource res = new ClassPathResource("demo-data/llm_step_records.json");
        try (InputStream in = res.getInputStream()) {
            List<RecordPayloadDto> list = objectMapper.readValue(
                    in, new TypeReference<List<RecordPayloadDto>>() {});
            for (RecordPayloadDto d : list) {
                if (d.getType() == null) d.setType("record");
            }
            return list;
        }
    }

    private AgentGuidePayloadDto loadGuide() throws Exception {
        ClassPathResource res = new ClassPathResource("demo-data/agent_guide.json");
        try (InputStream in = res.getInputStream()) {
            AgentGuidePayloadDto g = objectMapper.readValue(in, AgentGuidePayloadDto.class);
            if (g.getType() == null) g.setType("agent_guide");
            return g;
        }
    }

    public static class DemoStatus {
        public final boolean running;
        public final int total;
        public final int completed;

        public DemoStatus(boolean running, int total, int completed) {
            this.running = running;
            this.total = total;
            this.completed = completed;
        }
    }
}
