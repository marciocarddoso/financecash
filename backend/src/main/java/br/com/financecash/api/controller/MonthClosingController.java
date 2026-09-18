package br.com.financecash.api.controller;

import br.com.financecash.application.dto.MonthClosingResponse;
import br.com.financecash.application.service.MonthClosingService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/month-closing")
@Validated
public class MonthClosingController {

    private final MonthClosingService monthClosingService;

    public MonthClosingController(MonthClosingService monthClosingService) {
        this.monthClosingService = monthClosingService;
    }

    /** Fechamento do mês: previsto vs. realizado, geral e por categoria. */
    @GetMapping
    public MonthClosingResponse get(
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return monthClosingService.getMonthClosing(year, month);
    }
}
