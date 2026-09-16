package br.com.vanep.auth.password.model;

import br.com.vanep.auth.token.model.OneTimeTokenModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetTokenModel extends OneTimeTokenModel {}
