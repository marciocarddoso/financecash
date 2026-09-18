package br.com.financecash.boleto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos de teste gerados e validados com um script Python que monta uma linha
 * digitável sintética (calculando os dígitos verificadores mod10 e o DV geral
 * mod11 "de verdade") e confirma o round-trip antes de portar para Java — por
 * isso os valores esperados aqui são exatos, não arbitrários.
 */
class BoletoLinhaDigitavelParserTest {

    @Test
    void deveDecodificarLinhaDigitavelDoBradesco() {
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(
                "23791.23454 67890.123457 67890.123457 3 13810000123456");

        assertThat(result.valid()).isTrue();
        assertThat(result.bankCode()).isEqualTo("237");
        assertThat(result.bankName()).isEqualTo("Bradesco");
        assertThat(result.amount()).isEqualByComparingTo("1234.56");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(result.fieldChecksumsOk()).isTrue();
    }

    @Test
    void deveDecodificarLinhaDigitavelDoC6Bank() {
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(
                "33699.98871 76655.443323 21100.998877 1 14240000008990");

        assertThat(result.valid()).isTrue();
        assertThat(result.bankCode()).isEqualTo("336");
        assertThat(result.bankName()).isEqualTo("C6 Bank");
        assertThat(result.amount()).isEqualByComparingTo("89.90");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 4, 22));
    }

    @Test
    void deveDecodificarBancoDesconhecidoComNomeGenerico() {
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(
                "99990.00004 00000.000000 00000.000018 5 16920000000500");

        assertThat(result.valid()).isTrue();
        assertThat(result.bankName()).isEqualTo("Banco 999");
        assertThat(result.amount()).isEqualByComparingTo("5.00");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    @Test
    void deveAceitarLinhaColadaSemFormatacao() {
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(
                "23791234546789012345767890123457313810000123456".substring(0, 47));

        // mesma linha do primeiro teste, só que sem pontos/espaços
        BoletoLinhaDigitavel comFormatacao = BoletoLinhaDigitavelParser.parse(
                "23791.23454 67890.123457 67890.123457 3 13810000123456");

        assertThat(result.bankCode()).isEqualTo(comFormatacao.bankCode());
        assertThat(result.amount()).isEqualByComparingTo(comFormatacao.amount());
        assertThat(result.dueDate()).isEqualTo(comFormatacao.dueDate());
    }

    @Test
    void deveRejeitarQuantidadeDeDigitosInvalida() {
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse("123456789");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("47 dígitos");
    }

    @Test
    void deveSinalizarBoletoDeConcessionariaComoNaoSuportado() {
        String quarentaEOitoDigitos = "1".repeat(48);
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(quarentaEOitoDigitos);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("concessionária");
    }

    @Test
    void deveInvalidarQuandoDigitoVerificadorDeCampoNaoConfere() {
        // troca o último dígito do campo 1 (DV) para forçar inconsistência
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(
                "23791.23459 67890.123457 67890.123457 3 13810000123456");

        assertThat(result.valid()).isFalse();
        assertThat(result.fieldChecksumsOk()).isFalse();
    }

    @Test
    void deveRetornarValorNuloQuandoCampoValorEstaZerado() {
        // fator válido, valor 0000000000 -> boleto sem valor fixo (pagador informa)
        BoletoLinhaDigitavel result = BoletoLinhaDigitavelParser.parse(
                "00190.00009 00000.000000 00000.000000 0 13810000000000");

        assertThat(result.amount()).isNull();
    }
}
