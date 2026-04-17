package com.skyflux.kiln;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Kiln")
@SpringBootApplication(scanBasePackages = "com.skyflux.kiln")
public class KilnApplication {

    public static void main(String[] args) {
        SpringApplication.run(KilnApplication.class, args);
    }

}
