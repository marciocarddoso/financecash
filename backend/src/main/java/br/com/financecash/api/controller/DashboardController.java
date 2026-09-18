package br.com.financecash.api.controller;

import br.com.financecash.application.dto.DashboardResponse;
import br.com.financecash.application.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Contas do dia e saldo necessário para o dia/mês — a tela inicial do sistema. */
    @GetMapping
    public DashboardResponse getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        return dashboardService.getDashboard(referenceDate != null ? referenceDate : LocalDate.now());
    }
}
