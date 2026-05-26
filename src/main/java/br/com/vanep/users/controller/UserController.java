package br.com.vanep.users.controller;

import br.com.vanep.users.dto.UserPayloadDto;
import br.com.vanep.users.dto.UserResponseDto;
import br.com.vanep.users.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Cadastro e consulta de usuários")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponseDto create(@RequestBody UserPayloadDto payload) {
    return userService.create(payload);
  }

  @GetMapping
  public List<UserResponseDto> findAll() {
    return userService.findAll();
  }

  @GetMapping("/{token}")
  public UserResponseDto findByToken(@PathVariable String token) {
    return userService.findByToken(token);
  }

  @PutMapping("/{token}")
  public UserResponseDto update(@PathVariable String token, @RequestBody UserPayloadDto payload) {
    return userService.update(token, payload);
  }

  @DeleteMapping("/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String token) {
    userService.delete(token);
  }
}
