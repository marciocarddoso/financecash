package br.com.financecash.application.service;

import br.com.financecash.application.dto.NotificationPreferencesDTO;
import br.com.financecash.application.dto.NotificationPreferencesUpdateRequest;
import br.com.financecash.domain.model.AppUser;
import br.com.financecash.domain.repository.AppUserRepository;
import br.com.financecash.exception.ResourceNotFoundException;
import br.com.financecash.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesService {

    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;

    public UserPreferencesService(AppUserRepository appUserRepository, CurrentUserProvider currentUserProvider) {
        this.appUserRepository = appUserRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDTO getPreferences() {
        return NotificationPreferencesDTO.from(currentUserProvider.getCurrentUser());
    }

    @Transactional
    public NotificationPreferencesDTO updatePreferences(NotificationPreferencesUpdateRequest request) {
        // open-in-view está desligado (application.yml) — o AppUser que vem do
        // CurrentUserProvider foi carregado no filtro de autenticação, numa sessão do
        // Hibernate já fechada, e chega aqui "detached". Buscamos de novo pelo id dentro
        // desta transação para poder persistir a alteração com segurança.
        AppUser user = appUserRepository.findById(currentUserProvider.getCurrentUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        user.setNotifyDueSoonEmail(request.notifyDueSoonEmail());
        user.setNotifyNegativeBalanceEmail(request.notifyNegativeBalanceEmail());
        user.setNotifySmsEnabled(request.notifySmsEnabled());
        user.setPhoneNumber(request.phoneNumber());
        appUserRepository.save(user);

        return NotificationPreferencesDTO.from(user);
    }
}
