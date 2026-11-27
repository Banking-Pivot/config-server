package com.banking.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Boots the Config Server the way its pod runs it (k8s/deployment.yaml): "native" profile, Vault
 * disabled, and a folder that stands for the ConfigMap mounted in /config.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.vault.enabled=false",
      // No registry in the tests: the server must not try to register with a real Eureka.
      "eureka.client.enabled=false"
    })
@ActiveProfiles("native")
class MountedConfigMapTests {

  private static final String FILE = "demo-service.yml";
  private static final String CONTAINER = "spec.template.spec.containers[0].";
  private static final Path MOUNT = mount();

  @DynamicPropertySource
  static void mountedConfigMap(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.config.server.native.search-locations", () -> MOUNT.toUri().toString());
  }

  @LocalServerPort private int port;

  @Test
  void servesTheMountedFilesWithoutVault() {
    assertThat(served()).contains(FILE, "\"demo.timeout\":\"2s\"");
  }

  @Test
  void servesAChangedConfigMapWithoutRestart() throws IOException {
    Files.writeString(MOUNT.resolve(FILE), terms("5s"));
    try {
      assertThat(served())
          .contains("\"demo.timeout\":\"5s\"")
          .doesNotContain("\"demo.timeout\":\"2s\"");
    } finally {
      Files.writeString(MOUNT.resolve(FILE), terms("2s"));
    }
  }

  @Test
  void theDeploymentRunsTheServerThisWay() {
    Properties deployment = manifest("deployment.yaml");
    Map<String, String> env = new HashMap<>();
    for (int i = 0; deployment.containsKey(CONTAINER + "env[" + i + "].name"); i++) {
      env.put(
          deployment.getProperty(CONTAINER + "env[" + i + "].name"),
          deployment.getProperty(CONTAINER + "env[" + i + "].value"));
    }

    assertThat(env)
        .containsEntry("SPRING_PROFILES_ACTIVE", "native")
        .containsEntry("SPRING_CLOUD_VAULT_ENABLED", "false")
        .containsEntry("CONFIG_NATIVE_LOCATIONS", "file:/config/");
    assertThat(deployment.getProperty(CONTAINER + "volumeMounts[0].mountPath"))
        .isEqualTo("/config");
    assertThat(deployment.getProperty(CONTAINER + "volumeMounts[0].name"))
        .isEqualTo(deployment.getProperty("spec.template.spec.volumes[0].name"));
    assertThat(deployment.getProperty("spec.template.spec.volumes[0].configMap.name"))
        .isEqualTo("banking-config");
    assertThat(deployment.getProperty(CONTAINER + "ports[0].containerPort"))
        .isEqualTo(manifest("service.yaml").getProperty("spec.ports[0].targetPort"))
        .isEqualTo("8888");
  }

  private String served() {
    return RestClient.create("http://localhost:" + port)
        .get()
        .uri("/demo-service/default")
        .retrieve()
        .body(String.class);
  }

  /** A manifest of the k8s folder, flattened: {@code spec.template.spec.containers[0].image}. */
  private static Properties manifest(String name) {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new FileSystemResource(Path.of("k8s", name)));
    return yaml.getObject();
  }

  private static String terms(String timeout) {
    return "demo:\n  timeout: " + timeout + "\n";
  }

  /** What the pod sees in /config: one file per key of the ConfigMap. */
  private static Path mount() {
    try {
      Path folder = Files.createTempDirectory("config-map-");
      Files.writeString(folder.resolve(FILE), terms("2s"));
      folder.toFile().deleteOnExit();
      folder.resolve(FILE).toFile().deleteOnExit();
      return folder;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
