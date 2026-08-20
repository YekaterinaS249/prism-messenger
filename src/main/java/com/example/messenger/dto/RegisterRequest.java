package com.example.messenger.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "Логин обязателен")
    @Size(min = 3, max = 50, message = "Логин должен быть от 3 до 50 символов")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$",
            message = "Логин может содержать только латинские буквы, цифры, точку и подчёркивание")
    private String username;

    // Обязателен при регистрации — без него "забыли пароль" некуда отправлять письмо, и нет
    // смысла просить привязать его отдельно позже в настройках профиля.
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    @Size(max = 150, message = "Email должен быть не длиннее 150 символов")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 64, message = "Пароль должен быть от 8 до 64 символов")
    private String password;

    @NotBlank(message = "Отображаемое имя обязательно")
    @Size(min = 1, max = 100, message = "Отображаемое имя не может быть длиннее 100 символов")
    // Letters/marks/numbers/punctuation/plain space only — deliberately excludes the Unicode
    // Symbol categories emoji live in, the Format category zero-width/RTL-override spoofing
    // characters live in, and any whitespace variant other than a literal space (so a name typed
    // entirely in non-breaking or zero-width spaces can no longer sneak past @NotBlank looking empty).
    @Pattern(regexp = "^[\\p{L}\\p{M}\\p{N}\\p{P} ]+$",
            message = "Отображаемое имя не может содержать эмодзи и спецсимволы")
    private String displayName;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
