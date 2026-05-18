package br.com.vanep.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.vanep.users.Users;

public interface UsersRepository extends JpaRepository<Users, Long> {
}
