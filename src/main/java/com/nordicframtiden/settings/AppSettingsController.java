package com.nordicframtiden.settings;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class AppSettingsController {

    private final AppSettingsService service;

    public AppSettingsController(AppSettingsService service) {
        this.service = service;
    }

    @GetMapping("/mail")
    public Map<String, String> getMailSettings() {
        return service.getMailSettings();
    }

    @PutMapping("/mail")
    public Map<String, String> saveMailSettings(@RequestBody AppSettingsService.MailSettings settings) {
        service.saveMailSettings(settings);
        return service.getMailSettings();
    }
}
