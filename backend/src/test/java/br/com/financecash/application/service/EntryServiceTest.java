package br.com.financecash.application.service;

import br.com.financecash.application.dto.EntryBatchOperationResult;
import br.com.financecash.application.dto.EntryDTO;
import br.com.financecash.application.dto.EntryPageDTO;
import br.com.financecash.application.dto.PendingInvoiceConfirmationDTO;
import br.com.financecash.application.dto.TotalsRegime;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.model.InvoiceSettlement;
import br.com.financecash.domain.repository.AccountRepository;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.CreditCardRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.domain.repository.InvoiceSettlementRepository;
import br.com.financecash.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @Mock
    private EntryRepository entryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CreditCardRepository creditCardRepository;
    @Mock
    private InvoiceSettlementRepository invoiceSettlementRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private EntryService service() {
        return new EntryService(entryRepository, categoryRepository, accountRepository, creditCardRepository,
                invoiceSettlementRepository, currentUserProvider);
    }

    /** Projeção "vazia" pro totalizador (regime de competência), pra testes que não são sobre o totalizador em si. */
    private EntryRepository.EntryTotalsProjection totalsProjection(BigDecimal despesa, BigDecimal receita, long count) {
        return new EntryRepository.EntryTotalsProjection() {
            public BigDecimal getTotalDespesa() {
                return despesa;
            }

            public BigDecimal getTotalReceita() {
                return receita;
            }

            public long getEntryCount() {
                return count;
            }
        };
    }

    private EntryRepository.CreditCardTotalProjection cardTotalProjection(
            UUID creditCardId, String cardName, String bankName, String brand, BigDecimal despesa, BigDecimal receita) {
        return new EntryRepository.CreditCardTotalProjection() {
            public UUID getCreditCardId() {
                return creditCardId;
            }

            public String getCardName() {
                return cardName;
            }

            public String getBankName() {
                return bankName;
            }

            public String getBrand() {
                return brand;
            }

            public BigDecimal getDespesa() {
                return despesa;
            }

            public BigDecimal getReceita() {
                return receita;
            }
        };
    }

    private InvoiceSettlementRepository.CreditCardCashTotalProjection cardCashProjection(
            UUID creditCardId, String cardName, String bankName, String brand, BigDecimal despesa) {
        return new InvoiceSettlementRepository.CreditCardCashTotalProjection() {
            public UUID getCreditCardId() {
                return creditCardId;
            }

            public String getCardName() {
                return cardName;
            }

            public String getBankName() {
                return bankName;
            }

            public String getBrand() {
                return brand;
            }

            public BigDecimal getDespesa() {
                return despesa;
            }
        };
    }

    /**
     * Deixa os mocks de totalizador (regime de competência) "vazios" por padrão — usado pelos
     * testes que não são sobre o totalizador em si, pra service().search(...) não estourar
     * NullPointerException tentando fazer stream() de uma lista não mockada.
     */
    private void stubEmptyAccrualTotals() {
        lenient().when(entryRepository.sumByFiltersAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(totalsProjection(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        lenient().when(entryRepository.sumCardTotalsAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(invoiceSettlementRepository.findByCreditCardIdAndCycleDueDateBetween(any(), any(), any()))
                .thenReturn(List.of());
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    private Entry entry(AppUser owner) {
        Category category = Category.builder().id(UUID.randomUUID()).owner(owner).name("Mercado")
                .type(CategoryType.DESPESA).active(true).build();
        return Entry.builder().id(UUID.randomUUID()).owner(owner).description("Compra")
                .amount(new BigDecimal("50.00")).dueDate(LocalDate.of(2026, 9, 10)).type(EntryType.DESPESA)
                .status(EntryStatus.PENDENTE).origin(EntryOrigin.MANUAL).category(category).build();
    }

    private CreditCard creditCard(AppUser owner) {
        return CreditCard.builder().id(UUID.randomUUID()).owner(owner).name("C6 - Cartão")
                .bankName("C6 Bank").brand("MASTERCARD").closingDay(20).dueDay(28).active(true).build();
    }

    private Entry cardEntry(AppUser owner, CreditCard card, LocalDate dueDate, BigDecimal amount, EntryStatus status) {
        Category category = Category.builder().id(UUID.randomUUID()).owner(owner).name("Compras")
                .type(CategoryType.DESPESA).active(true).build();
        return Entry.builder().id(UUID.randomUUID()).owner(owner).description("Compra cartão")
                .amount(amount).dueDate(dueDate).type(EntryType.DESPESA).status(status)
                .origin(EntryOrigin.IMPORTADO_CARTAO).category(category).creditCard(card).build();
    }

    @Test
    void deveRepassarFiltrosOpcionaisParaORepositorio() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        stubEmptyAccrualTotals();

        Page<Entry> page = new PageImpl<>(List.of(entry(user)));
        when(entryRepository.search(eq(user.getId()), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class))).thenReturn(page);

        EntryPageDTO result = service().search(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, null, null, null, null, 0, 50);

        assertThat(result.items()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void deveRepassarFiltroDeCartaoParaORepositorio() {
        AppUser user = user();
        UUID creditCardId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        stubEmptyAccrualTotals();

        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().search(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, creditCardId, null, null, null, 0, 50);

        org.mockito.Mockito.verify(entryRepository).search(
                any(), any(), any(), any(), any(), any(), eq(creditCardId), any(), any(), any(Pageable.class));
    }

    /** Espelha deveRepassarFiltroDeCartaoParaORepositorio, mas pro filtro novo "por banco" (01/10, quinta rodada). */
    @Test
    void deveRepassarFiltroDeBancoParaORepositorio() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        stubEmptyAccrualTotals();

        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().search(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, null, "Bradesco", null, null, 0, 50);

        org.mockito.Mockito.verify(entryRepository).search(
                any(), any(), any(), any(), any(), any(), any(), eq("Bradesco"), any(), any(Pageable.class));
    }

    @Test
    void deveCalcularTotalizadorEIncluirLiquidacoesDoBancoQuandoFiltradoPorUmCartao() {
        // A pedido do Marcio (01/10): a tela de Lançamentos já filtrava por cartão/período, mas
        // não mostrava nenhum total pra conferência contra o valor real de uma fatura. Quando o
        // filtro é por um único cartão, o totalizador também traz o que o banco informou como
        // pagamento/financiamento daquela fatura (InvoiceSettlement), lado a lado.
        AppUser user = user();
        UUID creditCardId = UUID.randomUUID();
        CreditCard card = creditCard(user);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(entryRepository.sumByFiltersAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(totalsProjection(new BigDecimal("1000.00"), new BigDecimal("200.00"), 5));
        lenient().when(entryRepository.sumCardTotalsAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        InvoiceSettlement settlement = InvoiceSettlement.builder()
                .id(UUID.randomUUID()).creditCard(card).cycleDueDate(LocalDate.of(2026, 9, 15))
                .amount(new BigDecimal("5381.66")).transactionDate(LocalDate.of(2026, 9, 15))
                .description("Inclusao de Pagamento Ciclo Corrente").pluggyTransactionId("tx-1").build();
        when(invoiceSettlementRepository.findByCreditCardIdAndCycleDueDateBetween(
                eq(creditCardId), any(), any())).thenReturn(List.of(settlement));

        EntryPageDTO result = service().search(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, creditCardId, null, null, null, 0, 50);

        assertThat(result.totals().totalDespesa()).isEqualByComparingTo("1000.00");
        assertThat(result.totals().totalReceita()).isEqualByComparingTo("200.00");
        assertThat(result.totals().net()).isEqualByComparingTo("800.00");
        assertThat(result.totals().entryCount()).isEqualTo(5);
        assertThat(result.totals().regime()).isEqualTo(TotalsRegime.COMPETENCIA);
        assertThat(result.totals().bankSettlements()).hasSize(1);
        assertThat(result.totals().bankSettlements().get(0).amount()).isEqualByComparingTo("5381.66");
        assertThat(result.totals().bankSettlements().get(0).description()).isEqualTo("Inclusao de Pagamento Ciclo Corrente");
    }

    @Test
    void naoDeveIncluirLiquidacoesDoBancoQuandoNaoFiltradoPorCartao() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        stubEmptyAccrualTotals();

        EntryPageDTO result = service().search(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, null, null, null, null, 0, 50);

        assertThat(result.totals().bankSettlements()).isEmpty();
        org.mockito.Mockito.verify(invoiceSettlementRepository, org.mockito.Mockito.never())
                .findByCreditCardIdAndCycleDueDateBetween(any(), any(), any());
    }

    @Test
    void deveNormalizarDescricaoParaMinusculoAntesDeBuscar() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        stubEmptyAccrualTotals();

        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().search(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, null, null, "  Mercado  ", null, 0, 50);

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(entryRepository).search(
                any(), any(), any(), any(), any(), any(), any(), any(), descriptionCaptor.capture(), any(Pageable.class));
        assertThat(descriptionCaptor.getValue()).isEqualTo("mercado");
    }

    @Test
    void devePaginarComTamanhoPadraoQuandoSizeInvalido() {
        AppUser user = user();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        stubEmptyAccrualTotals();

        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().search(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, null, null, null, null, 0, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(entryRepository).search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void devePreencherQuebraPorCartaoNoRegimeDeCompetenciaComNomeDeExibicaoFormatado() {
        // A pedido do Marcio (01/10, quinta rodada): "quero ver o totalizador por cartão e por
        // banco" — a quebra por cartão vem junto do totalizador, com o nome já formatado
        // ("C6 Mastercard" em vez do nome bruto salvo no banco) e o bankName já normalizado.
        AppUser user = user();
        UUID creditCardId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(entryRepository.sumByFiltersAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(totalsProjection(new BigDecimal("500.00"), BigDecimal.ZERO, 3));
        when(entryRepository.sumCardTotalsAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(cardTotalProjection(
                        creditCardId, "C6 - Cartão", "C6 Bank", "MASTERCARD", new BigDecimal("500.00"), BigDecimal.ZERO)));
        lenient().when(invoiceSettlementRepository.findByCreditCardIdAndCycleDueDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        EntryPageDTO result = service().search(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null, null, null, null, null, 0, 50);

        assertThat(result.totals().creditCardTotals()).hasSize(1);
        assertThat(result.totals().creditCardTotals().get(0).creditCardId()).isEqualTo(creditCardId);
        assertThat(result.totals().creditCardTotals().get(0).displayName()).isEqualTo("C6 Mastercard");
        assertThat(result.totals().creditCardTotals().get(0).bankName()).isEqualTo("C6");
        assertThat(result.totals().creditCardTotals().get(0).despesa()).isEqualByComparingTo("500.00");
    }

    @Test
    void regimeDeCaixaDeveSomarLancamentosPagosForaDeCartaoMaisOQueOBancoInformouPorCartao() {
        // Decisão do Marcio (01/10, quinta rodada): no regime de caixa, o que sai/entra de
        // cartão não vem das compras individuais (que nascem PENDENTE, sem paymentDate real) —
        // vem do que o próprio banco informou como pago/financiado por fatura
        // (InvoiceSettlement), igual à lógica da planilha dele (uma linha por cartão/mês).
        AppUser user = user();
        UUID creditCardId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(entryRepository.sumByFiltersCash(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(totalsProjection(new BigDecimal("300.00"), new BigDecimal("50.00"), 2));
        when(invoiceSettlementRepository.sumByCreditCardCash(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cardCashProjection(
                        creditCardId, "C6 - Cartão", "C6 Bank", "MASTERCARD", new BigDecimal("4890.53"))));

        EntryPageDTO result = service().search(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                null, null, null, null, null, null, TotalsRegime.CAIXA, 0, 50);

        assertThat(result.totals().regime()).isEqualTo(TotalsRegime.CAIXA);
        assertThat(result.totals().totalDespesa()).isEqualByComparingTo("5190.53");
        assertThat(result.totals().totalReceita()).isEqualByComparingTo("50.00");
        assertThat(result.totals().entryCount()).isEqualTo(2);
        assertThat(result.totals().creditCardTotals()).hasSize(1);
        assertThat(result.totals().creditCardTotals().get(0).despesa()).isEqualByComparingTo("4890.53");
        assertThat(result.totals().creditCardTotals().get(0).receita()).isEqualByComparingTo("0");
        org.mockito.Mockito.verify(entryRepository, org.mockito.Mockito.never())
                .sumByFiltersAccrual(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deveAgruparFaturasPendentesPorCartaoEVencimento() {
        // Achado de 01/10: "faltar pagar no mês" ficava inflado por compras de cartão que nunca
        // saíam de PENDENTE mesmo com a fatura real já paga. Confirmação manual por fatura
        // (não automática — ver javadoc de PendingInvoiceConfirmationDTO) agrupa por
        // (cartão, vencimento) em vez de listar cada compra individualmente.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate dueDate = LocalDate.of(2026, 9, 15);
        LocalDate referenceDate = LocalDate.of(2026, 9, 28);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.findOverdueCardPurchases(user.getId(), referenceDate)).thenReturn(List.of(
                cardEntry(user, card, dueDate, new BigDecimal("100.00"), EntryStatus.PENDENTE),
                cardEntry(user, card, dueDate, new BigDecimal("50.00"), EntryStatus.PENDENTE)
        ));

        List<PendingInvoiceConfirmationDTO> result = service().getPendingInvoiceConfirmations(referenceDate);

        assertThat(result).hasSize(1);
        PendingInvoiceConfirmationDTO confirmation = result.get(0);
        assertThat(confirmation.creditCardId()).isEqualTo(card.getId());
        assertThat(confirmation.creditCardDisplayName()).isEqualTo("C6 Mastercard");
        assertThat(confirmation.dueDate()).isEqualTo(dueDate);
        assertThat(confirmation.entryCount()).isEqualTo(2);
        assertThat(confirmation.totalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void deveMarcarTodosOsLancamentosDaFaturaComoPagosAoConfirmar() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate dueDate = LocalDate.of(2026, 9, 15);
        Entry entry1 = cardEntry(user, card, dueDate, new BigDecimal("100.00"), EntryStatus.PENDENTE);
        Entry entry2 = cardEntry(user, card, dueDate, new BigDecimal("50.00"), EntryStatus.PENDENTE);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.findByOwnerIdAndCreditCardIdAndDueDateAndOriginAndStatus(
                user.getId(), card.getId(), dueDate, EntryOrigin.IMPORTADO_CARTAO, EntryStatus.PENDENTE))
                .thenReturn(List.of(entry1, entry2));

        EntryBatchOperationResult result = service().confirmInvoicePaid(card.getId(), dueDate, null);

        assertThat(result.affected()).isEqualTo(2);
        assertThat(entry1.getStatus()).isEqualTo(EntryStatus.PAGO);
        assertThat(entry1.getPaymentDate()).isEqualTo(dueDate);
        assertThat(entry2.getStatus()).isEqualTo(EntryStatus.PAGO);
    }

    @Test
    void deveUsarPaymentDateInformadoAoConfirmarFatura() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate dueDate = LocalDate.of(2026, 9, 15);
        LocalDate paymentDate = LocalDate.of(2026, 9, 16);
        Entry entry = cardEntry(user, card, dueDate, new BigDecimal("100.00"), EntryStatus.PENDENTE);
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(entryRepository.findByOwnerIdAndCreditCardIdAndDueDateAndOriginAndStatus(
                user.getId(), card.getId(), dueDate, EntryOrigin.IMPORTADO_CARTAO, EntryStatus.PENDENTE))
                .thenReturn(List.of(entry));

        service().confirmInvoicePaid(card.getId(), dueDate, paymentDate);

        assertThat(entry.getPaymentDate()).isEqualTo(paymentDate);
    }

    @Test
    void deveMarcarLancamentoComoExcluidoDoTotalizadorManualmente() {
        // A pedido do Marcio (01/10, quarta rodada): pra repasse/empréstimo pessoal que nenhum
        // sinal do banco identifica sozinho (ex.: dinheiro de um amigo pra comprar remédio pra
        // ele), o usuário marca manualmente que aquele lançamento não deve contar no total.
        AppUser user = user();
        Entry entry = entry(user);
        when(entryRepository.findById(entry.getId())).thenReturn(java.util.Optional.of(entry));

        EntryDTO result = service().excludeFromTotals(entry.getId());

        assertThat(entry.isExcludedFromTotals()).isTrue();
        assertThat(result.excludedFromTotals()).isTrue();
    }

    @Test
    void deveDesfazerExclusaoManualDoTotalizador() {
        AppUser user = user();
        Entry entry = entry(user);
        entry.setExcludedFromTotals(true);
        when(entryRepository.findById(entry.getId())).thenReturn(java.util.Optional.of(entry));

        EntryDTO result = service().includeInTotals(entry.getId());

        assertThat(entry.isExcludedFromTotals()).isFalse();
        assertThat(result.excludedFromTotals()).isFalse();
    }
}
