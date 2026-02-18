package com.digicache.services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;

public class DBInitializer {

    private final Connection connection;
    private static Connection sharedConnection;

    public DBInitializer(Connection connection) {
        this.connection = connection;
        sharedConnection = connection;
    }
    
    // Static method to get connection for controllers
    public static Connection getConnection() throws SQLException {
        if (sharedConnection == null || sharedConnection.isClosed()) {
            // Create a new connection if none exists
            sharedConnection = DriverManager.getConnection("jdbc:sqlite:digicache.db");
        }
        return sharedConnection;
    }

    private void runMigrationScript() throws SQLException {
        // Read and execute the migration script
        try {
            String migrationScript = new String(
                getClass().getClassLoader().getResourceAsStream("db/migration/add_timestamps.sql").readAllBytes()
            );
            
            for (String statement : migrationScript.split(";")) {
                if (!statement.trim().isEmpty()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(statement);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Warning: Migration script not found or could not be read");
        }
    }

    public void initializeDB() throws SQLException {
        Statement statement = connection.createStatement();
        
        // Create box_ids table
        String createBoxIdsTable = "CREATE TABLE IF NOT EXISTS box_ids (id TEXT PRIMARY KEY)";
        statement.execute(createBoxIdsTable);
        
        // Create images table with ALL required columns
        String createImagesTable = "CREATE TABLE IF NOT EXISTS images ("
                + "id TEXT PRIMARY KEY, "
                + "box_id TEXT NOT NULL, "
                + "image_data BLOB NOT NULL, "
                + "content_type TEXT DEFAULT 'image/jpeg', "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        statement.execute(createImagesTable);
        System.out.println("Images table created with box_id, content_type, and created_at columns");
        
        // Create box_contents table
        String createBoxContentsTable = "CREATE TABLE IF NOT EXISTS box_contents ("
                + "box_id TEXT, "
                + "item_id TEXT, "
                + "FOREIGN KEY (box_id) REFERENCES box_ids(id))";
        statement.execute(createBoxContentsTable);
        
        // Create background_images table
        String createBackgroundImagesTable = "CREATE TABLE IF NOT EXISTS background_images ("
                + "box_id TEXT PRIMARY KEY, "
                + "image_data BLOB NOT NULL, "
                + "FOREIGN KEY (box_id) REFERENCES box_ids(id))";
        statement.execute(createBackgroundImagesTable);
        System.out.println("Background images table created");
        
        statement.close();
    }
}