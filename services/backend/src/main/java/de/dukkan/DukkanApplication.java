package de.dukkan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the Dukkan grocery backend (modular monolith). */
@ConfigurationPropertiesScan
@SpringBootApplication
public class DukkanApplication {

  public static void main(String[] args) {
    SpringApplication.run(DukkanApplication.class, args);
  }
}
