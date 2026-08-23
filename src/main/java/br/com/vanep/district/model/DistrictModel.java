package br.com.vanep.district.model;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.location.LocationNameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Subdivisão de uma cidade, de profundidade variável via auto-FK: Taguatinga tem {@code parent}
 * nulo (filha direta de Brasília), QNL 5 tem Taguatinga como pai, Conjunto J tem QNL 5.
 *
 * <p>A restrição de unicidade declarada aqui existe para o schema gerado nos testes (H2). Em
 * produção quem manda é o índice parcial da migration {@code V21}, que também trata {@code
 * parent_id} nulo — ver o risco R2 do design.
 */
@Entity
@Table(
    name = "district",
    uniqueConstraints =
        @UniqueConstraint(
            name = "district_parent_city_name_active_key",
            columnNames = {"parent_id", "city_id", "normalized_name"}))
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class DistrictModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "city_id", nullable = false)
  private CityModel city;

  /**
   * Nulo quando o distrito é filho direto da cidade.
   *
   * <p>{@code EAGER} não é escolha de performance: o Hibernate recusa {@code LAZY} em associação
   * to-one cujo alvo declara {@code @SoftDelete}, e a regra 19 da constituição exige soft delete em
   * todo model removível. Efeito prático: carregar um distrito carrega a cadeia de ancestrais
   * junto. Isso é aceitável porque a profundidade é limitada (a maior observada nas fixtures é 3) e
   * porque é exatamente a cadeia que a busca por contenção precisa — ver D4 e D7.
   */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "parent_id")
  private DistrictModel parent;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "normalized_name", nullable = false, length = 128)
  private String normalizedName;

  @Column(name = "google_place_id", length = 255)
  private String googlePlaceId;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
    syncNormalizedName();
  }

  @PreUpdate
  void onUpdate() {
    syncNormalizedName();
  }

  /**
   * {@code normalized_name} é coluna derivada de {@code name}, não um campo que o chamador escolhe.
   * Mantê-la em sincronia aqui, e não em cada ponto de criação, é o que impede um nó de nascer com
   * a normalização errada — e um nó assim nunca casaria com nada, em silêncio (risco R2).
   */
  private void syncNormalizedName() {
    normalizedName = LocationNameNormalizer.normalize(name);
  }
}
