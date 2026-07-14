package br.com.vanep.school.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.school.dto.SchoolRequestDTO;
import br.com.vanep.school.dto.SchoolResponseDTO;
import br.com.vanep.school.mapper.SchoolMapper;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

  @Mock private SchoolRepository repository;
  @Mock private SchoolMapper mapper;
  @Mock private MessageSource messages;

  private SchoolService service;

  @BeforeEach
  void setUp() {
    service = new SchoolService(repository, mapper, messages);
  }

  private SchoolModel schoolWithToken(String token) {
    SchoolModel school = new SchoolModel();
    school.setToken(token);
    school.setName("Escola Teste");
    school.setCnpj("11222333000181");
    return school;
  }

  private SchoolResponseDTO responseFor(String token) {
    return new SchoolResponseDTO(
        token, "Escola Teste", "11222333000181", null, null, null, true, null);
  }

  private SchoolRequestDTO requestFor(String name, String cnpj) {
    return new SchoolRequestDTO(name, cnpj, null, null, null);
  }

  @Test
  void findAllReturnsPagedResponses() {
    SchoolModel school = schoolWithToken("abc");
    SchoolResponseDTO response = responseFor("abc");
    when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(school)));
    when(mapper.toResponse(school)).thenReturn(response);

    var result = service.findAll(Pageable.unpaged());

    assertThat(result.getContent()).containsExactly(response);
  }

  @Test
  void findByTokenReturnsResponse() {
    SchoolModel school = schoolWithToken("tok");
    SchoolResponseDTO response = responseFor("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));
    when(mapper.toResponse(school)).thenReturn(response);

    assertThat(service.findByToken("tok")).isEqualTo(response);
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
    SchoolRequestDTO request = requestFor("Escola Teste", "11222333000181");
    SchoolModel saved = schoolWithToken("tok");
    SchoolResponseDTO response = responseFor("tok");
    when(repository.existsByCnpj("11222333000181")).thenReturn(false);
    when(repository.save(any(SchoolModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    SchoolResponseDTO result = service.create(request);

    assertThat(result).isEqualTo(response);
    verify(repository).save(any(SchoolModel.class));
  }

  @Test
  void createThrows409WhenCnpjDuplicated() {
    when(repository.existsByCnpj("11222333000181")).thenReturn(true);

    assertThatThrownBy(() -> service.create(requestFor("Escola Teste", "11222333000181")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(repository, never()).save(any(SchoolModel.class));
  }

  @Test
  void createAllowsNullCnpj() {
    SchoolModel saved = schoolWithToken("tok");
    SchoolResponseDTO response = responseFor("tok");
    when(repository.save(any(SchoolModel.class))).thenReturn(saved);
    when(mapper.toResponse(saved)).thenReturn(response);

    SchoolResponseDTO result = service.create(requestFor("Escola Sem CNPJ", null));

    assertThat(result).isEqualTo(response);
    verify(repository, never()).existsByCnpj(any());
  }

  @Test
  void updatePersistsFields() {
    SchoolModel school = schoolWithToken("tok");
    SchoolResponseDTO response = responseFor("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));
    when(repository.save(school)).thenReturn(school);
    when(mapper.toResponse(school)).thenReturn(response);

    SchoolResponseDTO result =
        service.update(
            "tok", new SchoolRequestDTO("Escola Atualizada", "11222333000181", null, null, null));

    assertThat(result).isEqualTo(response);
    assertThat(school.getName()).isEqualTo("Escola Atualizada");
  }

  @Test
  void updateThrows409WhenCnpjBelongsToAnotherSchool() {
    SchoolModel school = schoolWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));
    when(repository.existsByCnpj("99888777000166")).thenReturn(true);

    assertThatThrownBy(() -> service.update("tok", requestFor("Escola Teste", "99888777000166")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(repository, never()).save(any(SchoolModel.class));
  }

  @Test
  void updateKeepsSameCnpjWithoutConflict() {
    SchoolModel school = schoolWithToken("tok");
    SchoolResponseDTO response = responseFor("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));
    when(repository.save(school)).thenReturn(school);
    when(mapper.toResponse(school)).thenReturn(response);

    SchoolResponseDTO result = service.update("tok", requestFor("Escola Teste", "11222333000181"));

    assertThat(result).isEqualTo(response);
    verify(repository, never()).existsByCnpj(any());
  }

  @Test
  void updateThrows404WhenNotFound() {
    when(repository.findByToken("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update("missing", requestFor("x", null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void deleteSoftDeletesSchool() {
    SchoolModel school = schoolWithToken("tok");
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));

    service.delete("tok");

    verify(repository).delete(school);
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
    SchoolModel school = schoolWithToken("tok");
    SchoolResponseDTO response = responseFor("tok");
    when(repository.existsDeletedByToken("tok")).thenReturn(true);
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));
    when(mapper.toResponse(school)).thenReturn(response);

    SchoolResponseDTO result = service.restore("tok");

    assertThat(result).isEqualTo(response);
    verify(repository).restoreByToken("tok");
  }

  @Test
  void restoreThrows409WhenAlreadyActive() {
    SchoolModel school = schoolWithToken("tok");
    when(repository.existsDeletedByToken("tok")).thenReturn(false);
    when(repository.findByToken("tok")).thenReturn(Optional.of(school));

    assertThatThrownBy(() -> service.restore("tok"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(repository, never()).restoreByToken(eq("tok"));
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
