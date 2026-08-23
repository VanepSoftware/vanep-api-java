package br.com.vanep.location.dto;

import br.com.vanep.location.enums.LocationLevel;

/**
 * Um componente do Google já classificado em nível da árvore.
 *
 * <p>{@code depth} só é significativo em {@link LocationLevel#DISTRICT}: 1 para filho direto da
 * cidade, 2 e 3 para os aninhados. É por ele que a hierarquia é montada — a ordem em que o Google
 * devolve os componentes não é estável entre chamadas (ver D11).
 *
 * <p>{@code sourceType} guarda o {@code type} do Google que produziu a classificação. É o que
 * permite preferir {@code administrative_area_level_2} a {@code locality} quando os dois aparecem,
 * em vez de deixar a posição no array decidir.
 */
public record LocationComponentDTO(
    LocationLevel level, int depth, String name, String shortName, String sourceType) {}
