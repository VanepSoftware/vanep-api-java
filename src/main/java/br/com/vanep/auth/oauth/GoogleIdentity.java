package br.com.vanep.auth.oauth;

public record GoogleIdentity(String subject, String email, String name) {}
