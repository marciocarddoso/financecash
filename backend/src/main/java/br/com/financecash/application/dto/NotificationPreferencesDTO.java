package br.com.financecash.application.dto;

import br.com.financecash.domain.model.AppUser;

/** Preferências de notificação do usuário logado — GET/PUT /api/me/notification-preferences. */
public record NotificationPreferencesDTO(
        boolean notifyDueSoonEmail,
        boolean notifyNegativeBalanceEmail,
        boolean notifySmsEnabled,
        String phoneNumber
) {
    public static NotificationPreferencesDTO from(AppUser user) {
        return new NotificationPreferencesDTO(
                user.isNotifyDueSoonEmail(),
                user.isNotifyNegativeBalanceEmail(),
                user.isNotifySmsEnabled(),
                user.getPhoneNumber());
    }
}
