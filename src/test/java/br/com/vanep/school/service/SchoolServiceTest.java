package br.com.vanep.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.school.dto.SchoolRequestDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.dto.SchoolUpdateRequestDTO;
import br.com.vanep.school.mapper.SchoolMapper;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {
  private static final Long SCHOOL_ID = 7L;
  private static final String TOKEN = "tok";

  @Mock private SchoolRepository repository;
  @Mock private SchoolMapper mapper;
  @Mock private AddressService addressService;
  @Mock private MessageSource messages;

  private SchoolService service;

  @BeforeEach
  void setUp() {
    service = new SchoolService(repository, mapper, addressService, messages);
    lenient().when(messages.getMessage(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(addressService.toResponsesByIds(any())).thenReturn(Map.of());
    lenient().when(addressService.toResponseOrNull(nullable(Long.class))).thenReturn(null);
  }

  private SchoolModel schoolWithToken(String token) {
    SchoolModel school = new SchoolModel();
    school.setId(SCHOOL_ID);
    school.setToken(token);
    school.setName("Escola Teste");
    return school;
  }

  private SchoolResponseDTO responseFor(String token) {
    return new SchoolResponseDTO(
        token, "Escola Teste", null, null, null, null, null, null, true, null);
  }

  private SchoolRequestDTO requestFor(String name) {
    return new SchoolRequestDTO(name, null);
  }

  private AddressRequestDTO addressRequest() {
    return new AddressRequestDTO("city-campinas", "13015904", "Rua da Escola", "1481", null);
  }

  private AddressResponseDTO addressResponse() {
    return new AddressResponseDTO(
        "addr-tok",
        "13015904",
        "Rua da Escola",
        "1481",
        null,
        "Centro",
        "city-campinas",
        "Campinas",
        "SP",
        true,
        null);
  }

  private SchoolUpdateRequestDTO patch(
      JsonNullable<String> name, JsonNullable<AddressRequestDTO> address) {
    return new SchoolUpdateRequestDTO(name, address);
  }

  private SchoolUpdateRequestDTO nameOnly(String name) {
    return patch(JsonNullable.of(name), JsonNullable.undefined());
  }

  @Test
  void findAllReturnsPagedResponses() {
    SchoolModel school = schoolWithToken("abc");
    SchoolResponseDTO response = responseFor("abc");
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(school)));
    when(mapper.toResponse(school, null)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    SchoolModel school = schoolWithToken(TOKEN);
    SchoolResponseDTO response = responseFor(TOKEN);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));
    when(mapper.toResponse(school, null)).thenReturn(response);

    assertThat(service.findByToken(TOKEN)).isEqualTo(response);
  }

  @Test
  void findByTokenThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findByToken("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void createPersistsSchool() {
    SchoolRequestDTO request = requestFor("Escola Teste");
    SchoolModel saved = schoolWithToken(TOKEN);
    SchoolResponseDTO response = responseFor(TOKEN);
    when(repository.save(any(SchoolModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved, null)).thenReturn(response);

    SchoolResponseDTO result = service.create(request);

    assertThat(result).isEqualTo(response);
    verify(repository).save(any(SchoolModel.class));
    verify(addressService, never()).upsertForSchool(any(), any());
  }

  @Test
  void createWithAddressUpsertsAfterPersist() {
    AddressRequestDTO address = addressRequest();
    SchoolRequestDTO request = new SchoolRequestDTO("Escola Teste", address);
    SchoolModel saved = schoolWithToken(TOKEN);
    SchoolResponseDTO response = responseFor(TOKEN);
    when(repository.save(any(SchoolModel.class))).thenReturn(saved);
    when(addressService.upsertForSchool(SCHOOL_ID, address)).thenReturn(addressResponse());
    when(repository.findById(SCHOOL_ID)).thenReturn(Optional.of(saved));
    when(mapper.toResponse(saved, null)).thenReturn(response);

    SchoolResponseDTO result = service.create(request);

    assertThat(result).isEqualTo(response);
    InOrder order = inOrder(repository, addressService);
    order.verify(repository).save(any(SchoolModel.class));
    order.verify(addressService).upsertForSchool(SCHOOL_ID, address);
  }

  @Test
  void updatePersistsFields() {
    SchoolModel school = schoolWithToken(TOKEN);
    SchoolResponseDTO response = responseFor(TOKEN);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));
    when(repository.save(school)).thenReturn(school);
    when(mapper.toResponse(school, null)).thenReturn(response);

    SchoolResponseDTO result = service.update(TOKEN, nameOnly("Escola Atualizada"));

    assertThat(result).isEqualTo(response);
    assertThat(school.getName()).isEqualTo("Escola Atualizada");
  }

  @Test
  void updatePresentNullAddressClearsAddress() {
    SchoolModel school = schoolWithToken(TOKEN);
    school.setAddressId(10L);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));
    when(repository.save(school)).thenReturn(school);
    when(repository.findById(SCHOOL_ID)).thenReturn(Optional.of(school));
    when(mapper.toResponse(eq(school), isNull())).thenReturn(responseFor(TOKEN));

    service.update(TOKEN, patch(JsonNullable.undefined(), JsonNullable.of(null)));

    verify(addressService).clearForSchool(SCHOOL_ID);
    verify(addressService, never()).upsertForSchool(any(), any());
  }

  @Test
  void updateNestedAddressUpsertsOwnedRow() {
    SchoolModel school = schoolWithToken(TOKEN);
    AddressRequestDTO request = addressRequest();
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));
    when(repository.save(school)).thenReturn(school);
    when(addressService.upsertForSchool(SCHOOL_ID, request)).thenReturn(addressResponse());
    when(repository.findById(SCHOOL_ID)).thenReturn(Optional.of(school));
    when(mapper.toResponse(eq(school), isNull())).thenReturn(responseFor(TOKEN));

    service.update(TOKEN, patch(JsonNullable.undefined(), JsonNullable.of(request)));

    verify(addressService).upsertForSchool(SCHOOL_ID, request);
  }

  @Test
  void updatePresentBlankNameThrows400AndLeavesStoredName() {
    SchoolModel school = schoolWithToken(TOKEN);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));

    assertThatThrownBy(() -> service.update(TOKEN, nameOnly("")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(school.getName()).isEqualTo("Escola Teste");
    verify(repository, never()).save(any(SchoolModel.class));
  }

  @Test
  void updatePresentNullNameThrows400() {
    SchoolModel school = schoolWithToken(TOKEN);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));

    assertThatThrownBy(() -> service.update(TOKEN, nameOnly(null)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void updateOmittedNameKeepsStoredName() {
    SchoolModel school = schoolWithToken(TOKEN);
    SchoolResponseDTO response = responseFor(TOKEN);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));
    when(repository.save(school)).thenReturn(school);
    when(mapper.toResponse(school, null)).thenReturn(response);

    service.update(TOKEN, patch(JsonNullable.undefined(), JsonNullable.undefined()));

    assertThat(school.getName()).isEqualTo("Escola Teste");
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", nameOnly("x")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesSchoolAfterClearingAddress() {
    SchoolModel school = schoolWithToken(TOKEN);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));

    service.delete(TOKEN);

    InOrder order = inOrder(addressService, repository);
    order.verify(addressService).clearForSchool(SCHOOL_ID);
    order.verify(repository).delete(school);
  }

  @Test
  void deleteThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void restoreRecoversDeletedSchool() {
    SchoolModel school = schoolWithToken(TOKEN);
    SchoolResponseDTO response = responseFor(TOKEN);
    when(repository.existsDeletedByToken(TOKEN)).thenReturn(true);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));
    when(mapper.toResponse(school, null)).thenReturn(response);

    SchoolResponseDTO result = service.restore(TOKEN);

    assertThat(result).isEqualTo(response);
    assertThat(result.address()).isNull();
    verify(repository).restoreByToken(TOKEN);
  }

  @Test
  void restoreThrows409WhenAlreadyActive() {
    SchoolModel school = schoolWithToken(TOKEN);
    when(repository.existsDeletedByToken(TOKEN)).thenReturn(false);
    when(repository.findByToken(TOKEN)).thenReturn(Optional.of(school));

    assertThatThrownBy(() -> service.restore(TOKEN))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(repository, never()).restoreByToken(eq(TOKEN));
  }

  @Test
  void restoreThrows404WhenNotFound() {
    when(repository.existsDeletedByToken("missing")).thenReturn(false);
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.restore("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }
}
