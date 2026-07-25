package com.ems.server.controller;

import com.ems.server.service.DemoService;
import com.ems.server.service.DemoService.DemoStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 데모 자동 진행 컨트롤러.
 *
 *  POST /api/demo/start?interval=2&max=20   — 데모 시작
 *  POST /api/demo/stop                       — 데모 중단
 *  GET  /api/demo/status                     — 현재 진행 상태
 */
@RestController
@RequestMapping("/api/demo")
@CrossOrigin(originPatterns = "*")
public class DemoController {

    @Autowired
    private DemoService demoService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
            @RequestParam(name = "interval", defaultValue = "2") int intervalSeconds,
            @RequestParam(name = "max", defaultValue = "20") int maxRecords) {
        Map<String, Object> body = new HashMap<>();
        try {
            DemoStatus status = demoService.start(intervalSeconds, maxRecords);
            body.put("success", true);
            body.put("message", "데모 시작됨 (interval=" + intervalSeconds + "s, max=" + maxRecords + ")");
            body.put("running", status.running);
            body.put("total", status.total);
            body.put("completed", status.completed);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("success", false);
            body.put("message", "데모 시작 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(body);
        }
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        demoService.stop();
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "데모 중단됨");
        return body;
    }

    @GetMapping("/status")
    public DemoStatus status() {
        return demoService.status();
    }
}
