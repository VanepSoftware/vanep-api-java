package br.com.vanep;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VanepApplication implements CommandLineRunner {
  public static void main(String[] args) {
    SpringApplication.run(VanepApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    System.out.println("Vanep Application Started");
  }
}
