package br.com.vanep.driverservicearea.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.state.model.StateModel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sem Spring, sem banco, sem servlet: a policy do D8 recebe a cadeia pronta. Se estes testes
 * precisassem preparar estado no banco, a regra estaria dependendo dele — que é exatamente o
 * defeito que o D8 existe para corrigir.
 */
class ServiceAreaGranularityPolicyTest {

  private CityModel city(String name, String uf, boolean stateRequires, Boolean cityOverride) {
    CountryModel country = new CountryModel();
    country.setIsoCode("BR");

    StateModel state = new StateModel();
    state.setUf(uf);
    state.setCountry(country);
    state.setRequiresDistrict(stateRequires);

    CityModel city = new CityModel();
    city.setName(name);
    city.setState(state);
    city.setRequiresDistrict(cityOverride);
    return city;
  }

  private ResolvedLocationChainDTO chain(CityModel city, boolean hasDistrictComponent) {
    List<DistrictModel> districts = List.of();
    if (hasDistrictComponent) {
      DistrictModel district = new DistrictModel();
      district.setName("Taguatinga");
      district.setCity(city);
      districts = List.of(district);
    }
    return new ResolvedLocationChainDTO(
        city.getState().getCountry(), city.getState(), city, districts, hasDistrictComponent);
  }

  @Test
  void rejectsWholeCityWhenTheStateRequiresADistrict() {
    CityModel brasilia = city("Brasília", "DF", true, null);

    assertThat(ServiceAreaGranularityPolicy.isAcceptable(chain(brasilia, false))).isFalse();
  }

  @Test
  void acceptsWholeCityWhenTheStateDoesNotRequireADistrict() {
    CityModel formosa = city("Formosa", "GO", false, null);

    assertThat(ServiceAreaGranularityPolicy.isAcceptable(chain(formosa, false))).isTrue();
  }

  @Test
  void acceptsWholeCityWhenTheCityOverridesTheStateFlag() {
    CityModel itapetininga = city("Itapetininga", "SP", true, false);

    assertThat(ServiceAreaGranularityPolicy.isAcceptable(chain(itapetininga, false))).isTrue();
  }

  @Test
  void rejectsWholeCityWhenTheCityOverridesToRequireEvenUnderALenientState() {
    CityModel grande = city("Cidade Grande", "GO", false, true);

    assertThat(ServiceAreaGranularityPolicy.isAcceptable(chain(grande, false))).isFalse();
  }

  @Test
  void acceptsAnyChainThatCarriesADistrictComponent() {
    assertThat(
            ServiceAreaGranularityPolicy.isAcceptable(
                chain(city("Brasília", "DF", true, null), true)))
        .isTrue();
    assertThat(
            ServiceAreaGranularityPolicy.isAcceptable(
                chain(city("Formosa", "GO", false, null), true)))
        .isTrue();
  }

  /**
   * O furo temporal do D8: a decisão não pode depender de quantos distritos existem na árvore. Aqui
   * não há árvore alguma — a policy nem tem como consultá-la — e a cadeia continua sendo rejeitada.
   * A formulação antiga aceitaria este cadastro, e o motorista apareceria em toda busca do DF para
   * sempre.
   */
  @Test
  void rejectsWholeCityEvenWhenNoDistrictExistsAnywhereYet() {
    CityModel brasiliaEmptyTree = city("Brasília", "DF", true, null);

    assertThat(ServiceAreaGranularityPolicy.isAcceptable(chain(brasiliaEmptyTree, false)))
        .isFalse();
  }

  @Test
  void readsTheCuratedFlagThroughTheChainWithoutQueryingAnything() {
    assertThat(ServiceAreaGranularityPolicy.requiresDistrict(city("Brasília", "DF", true, null)))
        .isTrue();
    assertThat(ServiceAreaGranularityPolicy.requiresDistrict(city("Formosa", "GO", false, null)))
        .isFalse();
    assertThat(
            ServiceAreaGranularityPolicy.requiresDistrict(city("Itapetininga", "SP", true, false)))
        .isFalse();
  }
}
