package com.digicache.controllers;

import com.digicache.services.DBInitializer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.*;

@RestController
@RequestMapping("/api/text")
@CrossOrigin(origins = "*")
public class TextController {
    
    private Connection connection;
    
    public TextController() {
        // Initialize database connection
        try {
            connection = DBInitializer.getConnection();
            if (connection == null) {
                throw new SQLException("Database connection is null");
            }
            createTextsTableIfNotExists();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize TextController: " + e.getMessage(), e);
        }
    }
    
    // Create texts table if it doesn't exist
    private void createTextsTableIfNotExists() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS texts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                box_id TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Texts table created or already exists");
        }
    }
    
    /**
     * Save text to a box
     * POST /api/text/save
     * Body: { "boxId": "box1", "content": "Your text here" }
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveTextToBox(@RequestBody String requestBodyJson) {
        try {
            // Parse the JSON request body
            JsonParser parser = new JsonParser();
            JsonObject requestBody = parser.parse(requestBodyJson).getAsJsonObject();
            
            String boxId = requestBody.get("boxId").getAsString();
            String content = requestBody.get("content").getAsString();
            
            // Validate content length (max 500 characters)
            if (content.length() > 500) {
                return ResponseEntity.status(400)
                    .body("{\"error\": \"Text content exceeds 500 character limit\"}");
            }
            
            if (content.trim().isEmpty()) {
                return ResponseEntity.status(400)
                    .body("{\"error\": \"Text content cannot be empty\"}");
            }
            
            // Save to database
            String insertSQL = """
                INSERT INTO texts (box_id, content)
                VALUES (?, ?)
            """;
            
            int generatedId;
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, boxId);
                pstmt.setString(2, content);
                pstmt.executeUpdate();
                
                // Get the generated ID
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    generatedId = rs.getInt(1);
                } else {
                    throw new SQLException("Failed to get generated ID");
                }
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("message", "Text saved successfully");
            response.addProperty("id", generatedId);
            response.addProperty("boxId", boxId);
            response.addProperty("content", content);
            
            return ResponseEntity.ok(response.toString());
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to save text: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get all texts for a specific box
     * GET /api/text/box/{boxId}
     */
    @GetMapping("/box/{boxId}")
    public ResponseEntity<String> getTextsByBox(@PathVariable String boxId) {
        try {
            String selectSQL = "SELECT * FROM texts WHERE box_id = ? ORDER BY created_at DESC";
            JsonArray textsArray = new JsonArray();
            
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setString(1, boxId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    JsonObject text = new JsonObject();
                    text.addProperty("id", rs.getInt("id"));
                    text.addProperty("boxId", rs.getString("box_id"));
                    text.addProperty("content", rs.getString("content"));
                    text.addProperty("createdAt", rs.getString("created_at"));
                    
                    textsArray.add(text);
                }
            }
            
            return ResponseEntity.ok(textsArray.toString());
            
        } catch (SQLException e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to get texts: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Delete a text from a box
     * DELETE /api/text/{textId}
     */
    @DeleteMapping("/{textId}")
    public ResponseEntity<String> deleteText(@PathVariable int textId) {
        try {
            String deleteSQL = "DELETE FROM texts WHERE id = ?";
            
            try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, textId);
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    JsonObject response = new JsonObject();
                    response.addProperty("message", "Text deleted successfully");
                    response.addProperty("textId", textId);
                    return ResponseEntity.ok(response.toString());
                } else {
                    return ResponseEntity.status(404)
                        .body("{\"error\": \"Text not found\"}");
                }
            }
            
        } catch (SQLException e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to delete text: " + e.getMessage() + "\"}");
        }
    }
}
