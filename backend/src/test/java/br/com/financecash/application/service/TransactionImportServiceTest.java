package br.com.financecash.application.service;

import br.com.financecash.domain.model.Account;
import br.com.financecash.domain.model.AccountType;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.Category;
import br.com.financecash.domain.model.CategoryType;
import br.com.financecash.domain.model.CreditCard;
import br.com.financecash.domain.model.Entry;
import br.com.financecash.domain.model.EntryOrigin;
import br.com.financecash.domain.model.EntryStatus;
import br.com.financecash.domain.model.EntryType;
import br.com.financecash.domain.model.InvoiceSettlement;
import br.com.financecash.domain.repository.CategoryRepository;
import br.com.financecash.domain.repository.EntryRepository;
import br.com.financecash.domain.repository.InvoiceSettlementRepository;
import br.com.financecash.openfinance.PluggyClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock
    private PluggyClient pluggyClient;
    @Mock
    private EntryRepository entryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private InvoiceSettlementRepository invoiceSettlementRepository;

    private TransactionImportService service() {
        return new TransactionImportService(pluggyClient, entryRepository, categoryRepository, invoiceSettlementRepository);
    }

    private AppUser user() {
        return AppUser.builder().id(UUID.randomUUID()).email("marcio@example.com").name("Marcio").build();
    }

    private Account account(AppUser owner) {
        return Account.builder().id(UUID.randomUUID()).owner(owner).name("Bradesco - Conta Corrente")
                .bankName("Banco Bradesco").type(AccountType.CORRENTE).active(true).build();
    }

    private CreditCard creditCard(AppUser owner) {
        return CreditCard.builder().id(UUID.randomUUID()).owner(owner).name("Bradesco - Cartão")
                .bankName("Banco Bradesco").closingDay(25).dueDay(5).active(true).build();
    }

    @Test
    void deveImportarTransacaoDeContaComoLancamentoJaPago() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-1", "Uber", new BigDecimal("-35.90"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null, null)
        ));
        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(
                user.getId(), "Uber", LocalDate.of(2026, 9, 14), new BigDecimal("35.90"))).thenReturn(false);
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForAccount(user, account, "acc-1", from, to);

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        Entry saved = captor.getValue();
        assertThat(saved.getDescription()).isEqualTo("Uber");
        assertThat(saved.getAmount()).isEqualByComparingTo("35.90");
        assertThat(saved.getType()).isEqualTo(EntryType.DESPESA);
        assertThat(saved.getStatus()).isEqualTo(EntryStatus.PAGO);
        assertThat(saved.getOrigin()).isEqualTo(EntryOrigin.IMPORTADO_EXTRATO);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(saved.getPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(saved.getAccount()).isEqualTo(account);
        assertThat(saved.getCreditCard()).isNull();
        assertThat(saved.isExcludedFromTotals()).isFalse();
    }

    @Test
    void deveMarcarComoExcluidoDoTotalizadorQuandoForTransferenciaEntreContasProprias() {
        // Achado em produção (01/10, terceira rodada): transferência entre as próprias contas
        // do usuário (ex. Bradesco -> Nubank) infla despesa E receita do totalizador sem nunca
        // aparecer no saldo líquido (uma perna cancela a outra) — confirmado com o Marcio que
        // deve ficar de fora da soma, mas continuar na lista de Lançamentos.
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-3", "Transferência enviada", new BigDecimal("-596.21"),
                        LocalDate.of(2026, 9, 10), "DEBIT", "POSTED", "Same person transfer", null)
        ));
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForAccount(user, account, "acc-1", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().isExcludedFromTotals()).isTrue();
    }

    @Test
    void deveMarcarComoExcluidoDoTotalizadorQuandoForPagamentoDeFaturaPeloLadoDaConta() {
        // Achado em produção (01/10, terceira rodada): o débito em conta que paga a fatura do
        // cartão (ex. Bradesco "PAGTO. POR DEB EM C/C") duplica um gasto já contado quando a
        // compra foi feita no cartão — mesmo marcador de descrição usado em
        // isInvoiceSettlementTransaction, reaproveitado aqui do lado da conta.
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-4", "PAGTO. POR DEB EM C/C", new BigDecimal("-2203.92"),
                        LocalDate.of(2026, 9, 10), "DEBIT", "POSTED", null, null)
        ));
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForAccount(user, account, "acc-1", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().isExcludedFromTotals()).isTrue();
    }

    void deveMarcarComoExcluidoDoTotalizadorQuandoForTransferenciaGenericaOuPix() {
        // Achado em produção (01/10, quarta rodada): o Marcio confirmou que, no caso dele,
        // "Transferências" e "Transferência - PIX" eram majoritariamente repasse pessoal
        // (empréstimo com a esposa, dinheiro de um amigo pra remédio), não renda/gasto real —
        // topou excluir por padrão (com o marcador manual como escape pra reverter um caso a caso).
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-5", "Transferência recebida", new BigDecimal("2800.00"),
                        LocalDate.of(2026, 9, 12), "CREDIT", "POSTED", "Transfer - PIX", null),
                new PluggyClient.TransactionInfo("tx-6", "Transferência enviada", new BigDecimal("-100.00"),
                        LocalDate.of(2026, 9, 12), "DEBIT", "POSTED", "Transfers", null)
        ));
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForAccount(user, account, "acc-1", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(entry -> assertThat(entry.isExcludedFromTotals()).isTrue());
    }

    @Test
    void deveMarcarComoReceitaQuandoAmountPositivoNumaConta() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-2", "Salário", new BigDecimal("5000.00"),
                        LocalDate.of(2026, 9, 5), "CREDIT", "POSTED", "Salário", null)
        ));
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Salário")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForAccount(user, account, "acc-1", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(EntryType.RECEITA);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("Salário");
        assertThat(categoryCaptor.getValue().getType()).isEqualTo(CategoryType.RECEITA);
    }

    @Test
    void deveTraduzirCategoriaDaPluggyParaPortugues() {
        // Achado em produção (30/09): ao contrário do que a doc da Pluggy dizia, category vem
        // preenchido mesmo no plano gratuito — só que em inglês, daí a tradução.
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-9", "iFood", new BigDecimal("-45.00"),
                        LocalDate.of(2026, 9, 12), "DEBIT", "POSTED", "Food delivery", null)
        ));
        when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Delivery de comida")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForAccount(user, account, "acc-1", from, to);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("Delivery de comida");
    }

    @Test
    void devePularTransacaoPendingDeConta() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-3", "Compra em análise", new BigDecimal("-10.00"),
                        LocalDate.of(2026, 9, 20), "DEBIT", "PENDING", null, null)
        ));

        int imported = service().importForAccount(user, account, "acc-1", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }

    @Test
    void devePularTransacaoDeContaJaImportadaAntes() {
        AppUser user = user();
        Account account = account(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-1", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-4", "Mercado", new BigDecimal("-150.00"),
                        LocalDate.of(2026, 9, 10), "DEBIT", "POSTED", null, null)
        ));
        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(
                user.getId(), "Mercado", LocalDate.of(2026, 9, 10), new BigDecimal("150.00"))).thenReturn(true);

        int imported = service().importForAccount(user, account, "acc-1", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
        verify(categoryRepository, never()).findByOwnerIdAndNameIgnoreCase(any(), anyString());
    }

    @Test
    void deveImportarCompraDeCartaoComoLancamentoPendenteUsandoDueDateDaFatura() {
        AppUser user = user();
        CreditCard card = creditCard(user); // closingDay=25, dueDay=5
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-5", "Netflix", new BigDecimal("39.90"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null, null)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        Entry saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(EntryStatus.PENDENTE);
        assertThat(saved.getOrigin()).isEqualTo(EntryOrigin.IMPORTADO_CARTAO);
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(saved.getPaymentDate()).isNull();
        assertThat(saved.getCreditCard()).isEqualTo(card);
        assertThat(saved.getAccount()).isNull();
    }

    @Test
    void deveImportarCompraDeCartaoComStatusPendingDaFaturaAindaAberta() {
        // Achado na documentação (29/09, ainda não confirmado com dado real): transações da
        // fatura ainda aberta (e parcelas futuras) vêm como PENDING em vez de POSTED — pra
        // cartão isso não pode ser motivo de pular, a compra já aconteceu de verdade.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-7", "Amazon", new BigDecimal("89.90"),
                        LocalDate.of(2026, 9, 20), "DEBIT", "PENDING", null, null)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isEqualTo(1);
        verify(entryRepository).save(any(Entry.class));
    }

    @Test
    void deveImportarCompraParceladaDeCartaoNormalmenteMesmoComMetadataDeParcelamento() {
        AppUser user = user();
        CreditCard card = creditCard(user); // closingDay=25, dueDay=5
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);
        var metadata = new PluggyClient.CreditCardMetadataInfo(3, 12, LocalDate.of(2026, 7, 14));

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-8", "Magazine Luiza Parc 03/12", new BigDecimal("150.00"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null, metadata)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        Entry saved = captor.getValue();
        assertThat(saved.getAmount()).isEqualByComparingTo("150.00");
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        // Marcio pediu (30/09) que a parcela e o total apareçam na tela de Lançamentos — isso só
        // funciona se o número/total de parcelas do metadata da Pluggy for persistido no Entry.
        assertThat(saved.getInstallmentNumber()).isEqualTo(3);
        assertThat(saved.getInstallmentsCount()).isEqualTo(12);
    }

    @Test
    void naoDevePreencherParcelaQuandoMetadataNaoIndicaCompraParcelada() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);
        // totalInstallments <= 1 (ou metadata ausente) não é parcelamento de verdade.
        var metadata = new PluggyClient.CreditCardMetadataInfo(1, 1, LocalDate.of(2026, 9, 14));

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-10", "Farmácia", new BigDecimal("60.00"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null, metadata)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForCreditCard(user, card, "acc-2", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().getInstallmentNumber()).isNull();
        assertThat(captor.getValue().getInstallmentsCount()).isNull();
    }

    @Test
    void deveExtrairParcelaDoTextoDaDescricaoQuandoNubankNaoMandaMetadataEstruturado() {
        // Achado em produção (30/09, com export real do banco do Marcio): creditCardMetadata da
        // Pluggy nunca veio preenchido pra nenhuma das 336 transações de cartão de 2026 (C6,
        // Bradesco, Nubank) — mas o Nubank manda a parcela como texto no fim da própria
        // descrição da transação, ex.: "Pmz Distribuidora 2/4". Esse teste usa exatamente esse
        // formato real.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-11", "Pmz Distribuidora 2/4", new BigDecimal("779.19"),
                        LocalDate.of(2026, 9, 14), "DEBIT", "POSTED", null, null)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForCreditCard(user, card, "acc-2", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        Entry saved = captor.getValue();
        // A descrição salva mantém o "2/4" original de propósito — ver javadoc de
        // parseInstallmentFromDescription (dedup compara description literal).
        assertThat(saved.getDescription()).isEqualTo("Pmz Distribuidora 2/4");
        assertThat(saved.getInstallmentNumber()).isEqualTo(2);
        assertThat(saved.getInstallmentsCount()).isEqualTo(4);
    }

    @Test
    void naoDeveExtrairParcelaDeDescricaoSemPadraoNDeM() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-12", "Tarifa Anuidade Diferenciada", new BigDecimal("98.00"),
                        LocalDate.of(2026, 9, 15), "DEBIT", "POSTED", null, null)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        service().importForCreditCard(user, card, "acc-2", from, to);

        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository).save(captor.capture());
        assertThat(captor.getValue().getInstallmentNumber()).isNull();
        assertThat(captor.getValue().getInstallmentsCount()).isNull();
    }

    @Test
    void devePularPagamentoDeFaturaDeCartao() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-6", "Pagamento de fatura", new BigDecimal("-1200.00"),
                        LocalDate.of(2026, 9, 5), "CREDIT", "POSTED", null, null)
        ));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }

    @Test
    void deveImportarEstornoDeCartaoComoReceitaEmVezDeIgnorarSilenciosamente() {
        // Achado em produção (01/10, a pedido do Marcio, com print real do app do C6): a tarifa
        // de anuidade diferenciada (R$98) é cobrada todo mês, mas o Marcio tem isenção por 1
        // ano, então o C6 manda um "Estorno Tarifa" negativo no mesmo dia cancelando ela. Antes
        // desse fix, qualquer transação de cartão com valor negativo era ignorada (pensado só
        // pro "Pagamento de fatura"), então esse estorno desaparecia e a tarifa ficava parecendo
        // uma despesa real de R$98/mês. Agora vira um Entry RECEITA separado.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-7", "Tarifa Anuidade", new BigDecimal("98.00"),
                        LocalDate.of(2026, 9, 2), "DEBIT", "POSTED", null, null),
                new PluggyClient.TransactionInfo("tx-8", "Estorno Tarifa", new BigDecimal("-98.00"),
                        LocalDate.of(2026, 9, 2), "CREDIT", "POSTED", null, null)
        ));
        lenient().when(categoryRepository.findByOwnerIdAndNameIgnoreCase(user.getId(), "Outros")).thenReturn(Optional.empty());
        lenient().when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isEqualTo(2);
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        List<Entry> saved = captor.getAllValues();

        Entry tarifa = saved.stream().filter(e -> e.getDescription().equals("Tarifa Anuidade")).findFirst().orElseThrow();
        assertThat(tarifa.getType()).isEqualTo(EntryType.DESPESA);
        assertThat(tarifa.getAmount()).isEqualByComparingTo("98.00");

        Entry estorno = saved.stream().filter(e -> e.getDescription().equals("Estorno Tarifa")).findFirst().orElseThrow();
        assertThat(estorno.getType()).isEqualTo(EntryType.RECEITA);
        assertThat(estorno.getAmount()).isEqualByComparingTo("98.00");
        assertThat(estorno.getStatus()).isEqualTo(EntryStatus.PENDENTE);
        assertThat(estorno.getCreditCard()).isEqualTo(card);
    }

    @Test
    void naoDeveDuplicarEstornoJaImportado() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);
        LocalDate dueDate = card.calculateInvoiceDueDate(LocalDate.of(2026, 9, 2));

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-8", "Estorno Tarifa", new BigDecimal("-98.00"),
                        LocalDate.of(2026, 9, 2), "CREDIT", "POSTED", null, null)
        ));
        when(entryRepository.existsByOwnerIdAndDescriptionAndDueDateAndAmount(
                user.getId(), "Estorno Tarifa", dueDate, new BigDecimal("98.00"))).thenReturn(true);

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }

    @Test
    void devePularLiquidacaoDeFaturaComTextosReaisDosBancos() {
        // Achado em produção (01/10, segunda rodada): nenhum dos 3 bancos do Marcio usa
        // literalmente "pagamento de fatura" pra liquidar a fatura — cada um usa um texto
        // diferente (confirmado comparando saldo líquido por cartão/mês com o valor real de
        // fatura do app do C6). Sem isInvoiceSettlementTransaction reconhecer esses textos,
        // cada um viraria um Entry RECEITA gigante, zerando as compras reais do mês.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-9", "PAGTO. POR DEB EM C/C", new BigDecimal("-2203.92"),
                        LocalDate.of(2026, 9, 10), "CREDIT", "POSTED", null, null),
                new PluggyClient.TransactionInfo("tx-10", "Inclusao de Pagamento Ciclo Corrente", new BigDecimal("-5381.66"),
                        LocalDate.of(2026, 9, 15), "CREDIT", "POSTED", null, null),
                new PluggyClient.TransactionInfo("tx-11", "Pagamento Ent parcelamento fat", new BigDecimal("-3032.91"),
                        LocalDate.of(2026, 10, 15), "CREDIT", "POSTED", null, null),
                new PluggyClient.TransactionInfo("tx-12", "Credito de Refinanciamento Saldo Financiado", new BigDecimal("-1857.62"),
                        LocalDate.of(2026, 10, 15), "CREDIT", "POSTED", null, null),
                new PluggyClient.TransactionInfo("tx-13", "Pagamento recebido", new BigDecimal("-3520.64"),
                        LocalDate.of(2026, 9, 22), "CREDIT", "POSTED", null, null)
        ));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }

    @Test
    void devePularLiquidacaoDeFaturaReconhecidaPelaCategoriaDaPluggy() {
        // A Pluggy manda a categoria "Credit card payment" (em inglês) pra esse tipo de
        // transação, independente do texto que cada banco usa na descrição — sinal mais
        // confiável que string matching. PluggyCategoryTranslator já traduz isso pra
        // "Pagamento de fatura de cartão".
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-14", "Texto nunca visto antes", new BigDecimal("-999.00"),
                        LocalDate.of(2026, 9, 15), "CREDIT", "POSTED", "Credit card payment", null)
        ));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }

    @Test
    void deveGravarInvoiceSettlementAoReconhecerLiquidacaoDeFatura() {
        // A transação de liquidação/financiamento de fatura não vira Entry, mas passa a ser
        // guardada como InvoiceSettlement (ver recordInvoiceSettlement) pra conferência
        // automática contra o total calculado — achado em produção (01/10) comparando saldo
        // líquido por cartão/mês com o app do C6.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);
        LocalDate txDate = LocalDate.of(2026, 9, 15);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-20", "Inclusao de Pagamento Ciclo Corrente",
                        new BigDecimal("-5381.66"), txDate, "CREDIT", "POSTED", null, null)
        ));
        when(invoiceSettlementRepository.existsByCreditCardIdAndPluggyTransactionId(card.getId(), "tx-20"))
                .thenReturn(false);

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
        ArgumentCaptor<InvoiceSettlement> captor = ArgumentCaptor.forClass(InvoiceSettlement.class);
        verify(invoiceSettlementRepository).save(captor.capture());
        InvoiceSettlement saved = captor.getValue();
        assertThat(saved.getCreditCard()).isEqualTo(card);
        assertThat(saved.getAmount()).isEqualByComparingTo("5381.66");
        assertThat(saved.getTransactionDate()).isEqualTo(txDate);
        assertThat(saved.getDescription()).isEqualTo("Inclusao de Pagamento Ciclo Corrente");
        assertThat(saved.getPluggyTransactionId()).isEqualTo("tx-20");
        assertThat(saved.getCycleDueDate()).isEqualTo(card.calculateInvoiceDueDate(txDate));
    }

    @Test
    void naoDeveDuplicarInvoiceSettlementJaGravada() {
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-21", "Inclusao de Pagamento Ciclo Corrente",
                        new BigDecimal("-5381.66"), LocalDate.of(2026, 9, 15), "CREDIT", "POSTED", null, null)
        ));
        when(invoiceSettlementRepository.existsByCreditCardIdAndPluggyTransactionId(card.getId(), "tx-21"))
                .thenReturn(true);

        service().importForCreditCard(user, card, "acc-2", from, to);

        verify(invoiceSettlementRepository, never()).save(any());
    }

    @Test
    void deveIgnorarComSegurancaTransacaoNegativaNaoReconhecida() {
        // Nem categoria "Credit card payment", nem texto de liquidação conhecido, nem palavra
        // de estorno ("estorno"/"cancelamento"/"reembolso") — caso desconhecido. Por segurança,
        // não vira Entry (evita contar um pagamento desconhecido como renda), só loga um aviso.
        AppUser user = user();
        CreditCard card = creditCard(user);
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 28);

        when(pluggyClient.listTransactions("acc-2", from, to)).thenReturn(List.of(
                new PluggyClient.TransactionInfo("tx-15", "Ajuste de saldo desconhecido", new BigDecimal("-123.45"),
                        LocalDate.of(2026, 9, 15), "CREDIT", "POSTED", null, null)
        ));

        int imported = service().importForCreditCard(user, card, "acc-2", from, to);

        assertThat(imported).isZero();
        verify(entryRepository, never()).save(any());
    }
}
