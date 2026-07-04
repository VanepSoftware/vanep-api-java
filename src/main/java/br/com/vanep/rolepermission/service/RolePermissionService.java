package br.com.vanep.rolepermission.service;

import br.com.vanep.rolepermission.dto.RolePermissionCreateRequestDTO;
import br.com.vanep.rolepermission.dto.RolePermissionResponseDTO;
import br.com.vanep.rolepermission.dto.RolePermissionUpdateRequestDTO;
import br.com.vanep.rolepermission.mapper.RolePermissionMapper;
import br.com.vanep.rolepermission.model.RolePermissionModel;
import br.com.vanep.rolepermission.repository.RolePermissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RolePermissionService {

  private final RolePermissionRepository bundles;
  private final RolePermissionMapper mapper;

  public RolePermissionService(RolePermissionRepository bundles, RolePermissionMapper mapper) {
    this.bundles = bundles;
    this.mapper = mapper;
  }

  public Page<RolePermissionResponseDTO> findAll(Pageable pageable) {
    return bundles.findAll(pageable).map(mapper::toResponse);
  }

  public RolePermissionResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public RolePermissionResponseDTO create(RolePermissionCreateRequestDTO request) {
    if (bundles.existsByName(request.name())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Nome já utilizado por outro bundle de permissões.");
    }
    RolePermissionModel bundle = new RolePermissionModel();
    bundle.setName(request.name());
    bundle.setPermissions(request.permissions());
    return mapper.toResponse(bundles.save(bundle));
  }

  @Transactional
  public RolePermissionResponseDTO update(String token, RolePermissionUpdateRequestDTO request) {
    RolePermissionModel bundle = requireByToken(token);
    if (bundles.existsByNameAndTokenNot(request.name(), token)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Nome já utilizado por outro bundle de permissões.");
    }
    bundle.setName(request.name());
    bundle.setPermissions(request.permissions());
    return mapper.toResponse(bundles.save(bundle));
  }

  @Transactional
  public void delete(String token) {
    bundles.delete(requireByToken(token));
  }

  private RolePermissionModel requireByToken(String token) {
    return bundles
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Bundle de permissões não encontrado."));
  }
}
