package br.com.financecash.api.controller;

import br.com.financecash.application.dto.BoletoParseRequest;
import br.com.financecash.boleto.BoletoLinhaDigitavel;
import br.com.financecash.boleto.BoletoLinhaDigitavelParser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boletos")
public class BoletoController {

    /**
     * Decodifica a linha digitável colada pelo usuário em banco/valor/vencimento,
     * para pré-preencher o formulário de lançamento manual — ver
     * docs/OPEN-FINANCE-E-BOLETOS.md para o contexto da automação de boletos.
     */
    @PostMapping("/parse-linha-digitavel")
    public ResponseEntity<BoletoLinhaDigitavel> parse(@Valid @RequestBody BoletoParseRequest request) {
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(request.linhaDigitavel());
        HttpStatus status = result.valid() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(result);
    }
}
