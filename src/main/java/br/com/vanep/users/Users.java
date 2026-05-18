package br.com.vanep.users;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table (name = "Users")
@Entity(name = "User")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Users {
    
    public Users(SignUpDataUsers dados) {
        this.names = dados.names();
        this.email = dados.email();
        this.username = dados.username();
        this.passwords = dados.passwords();
        this.cpf = dados.cpf();
        this.phone = dados.phone();
        this.address_id = dados.address_id();
        this.types = dados.types();

    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String token;

    @Enumerated(EnumType.STRING)
    private Type_user types;

    private String names;
    private String email;
    private String username;
    private String passwords;
    private String cpf;
    private String phone;
    private Integer address_id;
}
