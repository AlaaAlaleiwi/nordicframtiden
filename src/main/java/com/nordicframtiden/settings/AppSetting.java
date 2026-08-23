package com.nordicframtiden.settings;

import jakarta.persistence.*;

@Entity
@Table(name = "app_setting", uniqueConstraints = {
    @UniqueConstraint(name = "uk_app_setting_key", columnNames = "setting_key")
})
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 200)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 2000)
    private String value;

    public AppSetting() {
    }

    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
