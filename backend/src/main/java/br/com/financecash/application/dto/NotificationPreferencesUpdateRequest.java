package br.com.financecash.application.dto;

public record NotificationPreferencesUpdateRequest(
        boolean notifyDueSoonEmail,
        boolean notifyNegativeBalanceEmail,
        boolean notifySmsEnabled,
        String phoneNumber
) {
}
