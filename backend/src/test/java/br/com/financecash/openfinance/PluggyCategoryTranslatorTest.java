package br.com.financecash.openfinance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PluggyCategoryTranslatorTest {

    @Test
    void deveTraduzirCategoriaDeNivel1() {
        assertThat(PluggyCategoryTranslator.translate("Food and drinks")).isEqualTo("Alimentação");
        assertThat(PluggyCategoryTranslator.translate("Income")).isEqualTo("Renda");
        assertThat(PluggyCategoryTranslator.translate("Other")).isEqualTo("Outros");
    }

    @Test
    void deveTraduzirCategoriaDeNivel2() {
        assertThat(PluggyCategoryTranslator.translate("Eating out")).isEqualTo("Restaurantes");
        assertThat(PluggyCategoryTranslator.translate("Public transportation")).isEqualTo("Transporte público");
    }

    @Test
    void deveSerCaseInsensitive() {
        assertThat(PluggyCategoryTranslator.translate("FOOD AND DRINKS")).isEqualTo("Alimentação");
        assertThat(PluggyCategoryTranslator.translate("food and drinks")).isEqualTo("Alimentação");
    }

    @Test
    void deveTraduzirSubcategoriasDeInsuranceELegalObligations() {
        // Achado em produção (01/10): essas duas seções inteiras da doc estavam faltando no
        // mapa e viravam Category nova em inglês em vez de cair numa já existente em português
        // — causa raiz da duplicação de categorias que o Marcio reportou depois da janela de
        // sincronização subir pra 365 dias (mais histórico expôs subcategorias nunca vistas).
        assertThat(PluggyCategoryTranslator.translate("Vehicle insurance")).isEqualTo("Seguro veicular");
        assertThat(PluggyCategoryTranslator.translate("Health insurance")).isEqualTo("Seguro saúde");
        assertThat(PluggyCategoryTranslator.translate("Home insurance")).isEqualTo("Seguro residencial");
        assertThat(PluggyCategoryTranslator.translate("Life insurance")).isEqualTo("Seguro de vida");
        assertThat(PluggyCategoryTranslator.translate("Alimony")).isEqualTo("Pensão alimentícia");
        assertThat(PluggyCategoryTranslator.translate("Blocked balances")).isEqualTo("Saldos bloqueados");
    }

    @Test
    void deveTraduzirSubcategoriasQueNaoEstaoNaDocumentacaoDaPluggy() {
        // Achado em produção (01/10, segunda rodada): comparando o mapa contra a tabela REAL de
        // categorias do Marcio (não só contra docs.pluggy.ai/docs/transaction-categories), a
        // doc também estava incompleta — essas subcategorias nem aparecem nela, e "Accomodation"
        // tem grafia diferente da documentada ("Accommodation", com dois "m").
        assertThat(PluggyCategoryTranslator.translate("Accomodation")).isEqualTo("Hospedagem");
        assertThat(PluggyCategoryTranslator.translate("Cinema, theater and concerts")).isEqualTo("Cinema, teatro e shows");
        assertThat(PluggyCategoryTranslator.translate("Electricity")).isEqualTo("Energia elétrica");
        assertThat(PluggyCategoryTranslator.translate("Gas stations")).isEqualTo("Combustivel");
        assertThat(PluggyCategoryTranslator.translate("Mobile")).isEqualTo("Celular");
        assertThat(PluggyCategoryTranslator.translate("School")).isEqualTo("Escola");
        assertThat(PluggyCategoryTranslator.translate("Tolls and in vehicle payment")).isEqualTo("Pedágio e pagamento no veículo");
        assertThat(PluggyCategoryTranslator.translate("University")).isEqualTo("Faculdade");
        assertThat(PluggyCategoryTranslator.translate("Vehicle maintenance")).isEqualTo("Manutenção veicular");
        assertThat(PluggyCategoryTranslator.translate("Transfer - Bank Slip")).isEqualTo("Transferência - Boleto");
    }

    @Test
    void devePassarValorDireitoQuandoNaoTemTraducaoConhecida() {
        assertThat(PluggyCategoryTranslator.translate("Alguma categoria nova da Pluggy")).isEqualTo("Alguma categoria nova da Pluggy");
    }

    @Test
    void devePassarNullEBlankDireito() {
        assertThat(PluggyCategoryTranslator.translate(null)).isNull();
        assertThat(PluggyCategoryTranslator.translate("")).isEmpty();
    }
}
