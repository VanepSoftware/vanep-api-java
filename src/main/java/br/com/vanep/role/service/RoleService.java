package br.com.vanep.role.service;

import br.com.vanep.role.dto.RoleCreateRequestDTO;
import br.com.vanep.role.dto.RoleResponseDTO;
import br.com.vanep.role.dto.RoleUpdateRequestDTO;
import br.com.vanep.role.mapper.RoleMapper;
import br.com.vanep.role.model.RoleModel;
import br.com.vanep.role.repository.RoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoleService {

  private final RoleRepository roles;
  private final RoleMapper mapper;

  public RoleService(RoleRepository roles, RoleMapper mapper) {
    this.roles = roles;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public Page<RoleResponseDTO> findAll(Pageable pageable) {
    return roles.findAll(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public RoleResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public RoleResponseDTO create(RoleCreateRequestDTO request) {
    RoleModel role = new RoleModel();
    role.setName(request.name());
    role.setDescription(request.description());
    return mapper.toResponse(roles.save(role));
  }

  @Transactional
  public RoleResponseDTO update(String token, RoleUpdateRequestDTO request) {
    RoleModel role = requireByToken(token);
    role.setName(request.name());
    role.setDescription(request.description());
    return mapper.toResponse(roles.save(role));
  }

  @Transactional
  public void delete(String token) {
    roles.delete(requireByToken(token));
  }

  @Transactional
  public RoleResponseDTO restore(String token) {
    roles
        .findDeletedByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found."));
    roles.restore(token);
    return mapper.toResponse(requireByToken(token));
  }

  private RoleModel requireByToken(String token) {
    return roles
        .findByToken(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found."));
  }
}
