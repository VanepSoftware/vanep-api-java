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
        this.name = dados.name();
        this.email = dados.email();
        this.username = dados.username();
        this.password = dados.password();
        this.cpf = dados.cpf();
        this.phone = dados.phone();
        this.address_id = dados.address_id();
        this.type = dados.type();

    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String token;

    @Enumerated(EnumType.STRING)
    private Type type;

    private String name;
    private String email;
    private String username;
    private String password;
    private String cpf;
    private String phone;
    private Integer address_id;
}
