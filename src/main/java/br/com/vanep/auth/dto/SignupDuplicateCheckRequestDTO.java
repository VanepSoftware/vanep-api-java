package br.com.vanep.auth.dto;

/**
 * Fields the sign-up wizard checks before moving on. Any of them may be missing: each step sends
 * only the ones it has collected so far.
 */
public record SignupDuplicateCheckRequestDTO(String email, String document, String phone) {}
