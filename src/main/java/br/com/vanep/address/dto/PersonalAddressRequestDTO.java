package br.com.vanep.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /api/user/me/address}.
 *
 * <p>Só o {@code placeId} identifica o lugar. Rua, bairro, cidade e estado **não** entram aqui: o
 * backend re-resolve tudo pelo Place Details. Aceitar componentes do cliente deixaria a árvore
 * gravável por quem chama, e um nó plantado com nome errado envenena a busca de todo mundo.
 *
 * <p>{@code number} e {@code complement} vêm do usuário porque o Google frequentemente não os tem.
 */
public record PersonalAddressRequestDTO(
    @NotBlank @Size(max = 255) String placeId,
    @Size(max = 255) String sessionToken,
    @Size(max = 16) String number,
    @Size(max = 128) String complement) {}
