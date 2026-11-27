package com.banking.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server of the banking system: serves each service its settings from the banking-config
 * repository (git), or from a local folder or a mounted ConfigMap with the {@code native} profile.
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

  /**
   * Starts the server.
   *
   * @param args command line arguments, passed to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(ConfigServerApplication.class, args);
  }
}
