package br.com.vanep.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.vanep.repository.UsersRepository;
import br.com.vanep.users.SignUpDataUsers;
import br.com.vanep.users.Users;

@RestController
@RequestMapping("SignUpUsers")
public class UsersController {
    
    @Autowired
    private UsersRepository repository; 

    @PostMapping
    public void SignUp(@RequestBody SignUpDataUsers dados){
        repository.save(new Users(dados));
    }

}
