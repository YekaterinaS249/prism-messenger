package com.example.messenger.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AuthRequest {
    // Принимает и логин, и email — какой из двух ввёл пользователь, разбирается на сервере.
    @NotBlank(message = "Логин или email обязателен")
    @Size(max = 150, message = "Слишком длинное значение")
    private String username;
    @NotBlank(message = "Пароль обязателен")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
