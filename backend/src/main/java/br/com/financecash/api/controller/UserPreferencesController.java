package br.com.financecash.api.controller;

import br.com.financecash.application.dto.NotificationPreferencesDTO;
import br.com.financecash.application.dto.NotificationPreferencesUpdateRequest;
import br.com.financecash.application.service.UserPreferencesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notification-preferences")
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    public UserPreferencesController(UserPreferencesService userPreferencesService) {
        this.userPreferencesService = userPreferencesService;
    }

    @GetMapping
    public NotificationPreferencesDTO get() {
        return userPreferencesService.getPreferences();
    }

    @PutMapping
    public NotificationPreferencesDTO update(@RequestBody NotificationPreferencesUpdateRequest request) {
        return userPreferencesService.updatePreferences(request);
    }
}
