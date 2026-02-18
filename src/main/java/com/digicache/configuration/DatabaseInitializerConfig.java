package com.digicache.configuration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.digicache.services.DBInitializer;

import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseInitializerConfig implements CommandLineRunner {

    @Override
    public void run(String... args) {
        try {
            System.out.println("Creating database connection and initializing tables...");
            Connection connection = DBInitializer.getConnection();
            DBInitializer dbInitializer = new DBInitializer(connection);
            dbInitializer.initializeDB();
            System.out.println("Database tables initialized successfully - including background_images table");
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

