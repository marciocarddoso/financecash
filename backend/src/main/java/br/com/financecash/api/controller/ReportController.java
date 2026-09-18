package br.com.financecash.api.controller;

import br.com.financecash.application.dto.CategorySpendingReportResponse;
import br.com.financecash.application.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** "Onde eu mais gasto": mercado, farmácia, passeios etc., em um período. */
    @GetMapping("/spending-by-category")
    public CategorySpendingReportResponse spendingByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.spendingByCategory(from, to);
    }
}
