package br.com.vanep.location;

import java.util.Locale;
import java.util.Set;

/**
 * Fato curado: as cidades destes estados exigem granularidade abaixo da cidade na área de atuação
 * do motorista (D8).
 *
 * <p>Existe porque a árvore é lazy: quando o resolver cria uma linha de {@code state} que ainda não
 * existia, o {@code UPDATE} da migration {@code V21} já passou e não alcança a linha nova. Sem esta
 * classe, um estado criado sob demanda nasceria com {@code requires_district = false} — inclusive o
 * DF, que é a praça de lançamento e o motivo do D8 existir.
 *
 * <p>Mesma lista da seed da {@code V21}. Mudanças têm de acontecer nos dois lugares, ou a política
 * passa a depender de qual caminho criou a linha.
 */
public final class DistrictRequirementPolicy {

  private static final Set<String> UFS_REQUIRING_DISTRICT = Set.of("DF", "SP");

  private DistrictRequirementPolicy() {}

  public static boolean requiresDistrict(String uf) {
    return uf != null && UFS_REQUIRING_DISTRICT.contains(uf.toUpperCase(Locale.ROOT));
  }
}
