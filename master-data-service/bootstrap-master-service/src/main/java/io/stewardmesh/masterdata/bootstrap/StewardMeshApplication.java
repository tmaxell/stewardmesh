package io.stewardmesh.masterdata.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.stewardmesh.masterdata")
public class StewardMeshApplication {

    public static void main(String[] args) {
        SpringApplication.run(StewardMeshApplication.class, args);
    }
}
