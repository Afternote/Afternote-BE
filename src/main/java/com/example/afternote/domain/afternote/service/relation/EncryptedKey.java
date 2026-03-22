package com.example.afternote.domain.afternote.service.relation;

public enum EncryptedKey {
    ACCOUNT_ID("account_id"),
    ACCOUNT_PASSWORD("account_password");

    private final String value;

    EncryptedKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean matches(String keyName) {
        return value.equals(keyName);
    }
}
