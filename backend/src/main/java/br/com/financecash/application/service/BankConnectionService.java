package br.com.financecash.application.service;

import br.com.financecash.application.dto.BankConnectionCreateRequest;
import br.com.financecash.application.dto.BankConnectionDTO;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.model.BankConnection;
import br.com.financecash.domain.model.BankConnectionStatus;
import br.com.financecash.domain.repository.BankConnectionRepository;
import br.com.financecash.exception.BusinessException;
import br.com.financecash.openfinance.PluggyClient;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persiste e lista as conexões bancárias feitas pelo usuário via widget "Pluggy
 * Connect" — ver docs/OPEN-FINANCE-E-BOLETOS.md, seção 2.3. Ainda não sincroniza
 * saldo/transações (isso entra no AccountSyncService/TransactionImportService,
 * próxima fase do roadmap); por ora só guarda que a conexão existe.
 */
@Service
public class BankConnectionService {

    private final BankConnectionRepository bankConnectionRepository;
    private final PluggyClient pluggyClient;
    private final CurrentUserProvider currentUserProvider;

    public BankConnectionService(
            BankConnectionRepository bankConnectionRepository,
            PluggyClient pluggyClient,
            CurrentUserProvider currentUserProvider) {
        this.bankConnectionRepository = bankConnectionRepository;
        this.pluggyClient = pluggyClient;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public List<BankConnectionDTO> list() {
        AppUser user = currentUserProvider.getCurrentUser();
        return bankConnectionRepository.findByOwnerIdOrderByConnectedAtDesc(user.getId())
                .stream().map(BankConnectionDTO::from).toList();
    }

    /**
     * Chamado depois que o widget "Pluggy Connect" dispara onSuccess no frontend.
     * Busca os detalhes do item na Pluggy (nome do banco, status) em vez de confiar
     * em qualquer coisa que o próprio frontend diga sobre o item além do itemId.
     */
    @Transactional
    public BankConnectionDTO save(BankConnectionCreateRequest request) {
        AppUser user = currentUserProvider.getCurrentUser();
        if (bankConnectionRepository.existsByOwnerIdAndItemId(user.getId(), request.itemId())) {
            throw new BusinessException("Este banco já está conectado.");
        }

        PluggyClient.ItemInfo itemInfo = pluggyClient.getItem(request.itemId());

        BankConnection connection = BankConnection.builder()
                .owner(user)
                .itemId(itemInfo.itemId())
                .bankName(itemInfo.bankName())
                .status(mapStatus(itemInfo.status()))
                .build();
        return BankConnectionDTO.from(bankConnectionRepository.save(connection));
    }

    /**
     * A Pluggy tem estados mais granulares (ver PluggyClient.getItem); aqui simplificamos
     * para o que o usuário precisa saber: está funcionando (ATIVA), precisa reconectar
     * (EXPIRADA), ou tem algum problema que exige atenção (ERRO).
     */
    private BankConnectionStatus mapStatus(String pluggyStatus) {
        if (pluggyStatus == null) {
            return BankConnectionStatus.ERRO;
        }
        return switch (pluggyStatus) {
            case "UPDATED" -> BankConnectionStatus.ATIVA;
            case "OUTDATED" -> BankConnectionStatus.EXPIRADA;
            default -> BankConnectionStatus.ERRO;
        };
    }
}
