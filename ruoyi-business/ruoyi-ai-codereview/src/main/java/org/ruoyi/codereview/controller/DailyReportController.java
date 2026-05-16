package org.ruoyi.codereview.controller;

import lombok.RequiredArgsConstructor;
import org.ruoyi.codereview.service.DailyReportService;
import org.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 日报控制器
 */
@RestController
@RequestMapping("/codereview/report")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService dailyReportService;

    @GetMapping("/daily")
    public R<String> generateDailyReport() {
        return R.ok(dailyReportService.triggerDailyReport());
    }

    @GetMapping("/daily/{date}")
    public R<String> getDailyReport(@PathVariable String date) {
        try {
            LocalDate parseDate = LocalDate.parse(date);
            return R.ok(dailyReportService.generateDailyReportWithLlm(parseDate));
        } catch (Exception e) {
            return R.fail("日期格式错误，请使用 yyyy-MM-dd 格式");
        }
    }
}
