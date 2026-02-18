package com.digicache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DigiCacheApplication {
    public static void main(String[] args) {
        // Railway provides PORT environment variable
        String port = System.getenv("PORT");
        if (port != null) {
            System.setProperty("server.port", port);
        }
        SpringApplication.run(DigiCacheApplication.class, args);
    }
}
