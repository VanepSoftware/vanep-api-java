package br.com.vanep.auth.verification.model;

import br.com.vanep.auth.token.model.OneTimeTokenModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification_token")
public class EmailVerificationTokenModel extends OneTimeTokenModel {}
