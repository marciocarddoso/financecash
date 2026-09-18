package br.com.financecash.api.controller;

import br.com.financecash.application.dto.EntryImportSummary;
import br.com.financecash.application.service.EntryImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/entries")
public class EntryImportController {

    private final EntryImportService entryImportService;

    public EntryImportController(EntryImportService entryImportService) {
        this.entryImportService = entryImportService;
    }

    /**
     * Importa lançamentos em lote via CSV — ver Javadoc de {@link EntryImportService}
     * para o formato esperado das colunas. Usado tanto para trazer o histórico da
     * planilha (via {@code scripts/import_numbers_to_csv.py}) quanto para qualquer
     * outro CSV que siga o mesmo formato.
     */
    @PostMapping(value = "/import-csv", consumes = "multipart/form-data")
    public EntryImportSummary importCsv(@RequestParam("file") MultipartFile file) {
        return entryImportService.importCsv(file);
    }
}
