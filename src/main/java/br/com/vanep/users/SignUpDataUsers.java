package br.com.vanep.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SignUpDataUsers(

    @NotBlank
    String names,
    @NotBlank
    @Email 
    String email, 
    @NotBlank
    String username, 
    String passwords,
    @NotBlank
    @Pattern(regexp = "\\d{11}") 
    String cpf, 
    @NotBlank
    @Pattern(regexp = "\\d{11}") 
    String phone, 
    @NotNull
    Integer address_id,  
    @NotNull
    Type_user types) {

}
