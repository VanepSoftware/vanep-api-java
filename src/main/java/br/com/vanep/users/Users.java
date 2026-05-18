package br.com.vanep.users;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.security.MessageDigest;


import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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

    @PrePersist
    public void prePersist() {
        if (this.token == null) {
            this.token = generateToken();
        }
    }

    private String generateToken() {
        try {
            SecureRandom random = new SecureRandom();
            long rand = random.nextLong();
            long timestamp = System.nanoTime();

            String rawValue = rand + String.valueOf(timestamp) + random.nextLong();

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = sha1.digest(rawValue.getBytes());

            return HexFormat.of().formatHex(hashBytes).substring(0, 25);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao gerar token", e);
        }
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
