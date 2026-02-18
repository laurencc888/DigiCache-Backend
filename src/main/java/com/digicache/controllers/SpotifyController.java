package com.digicache.controllers;

import com.digicache.services.DBInitializer;
import com.digicache.services.SpotifyService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@RestController
@RequestMapping("/api/spotify")
@CrossOrigin(origins = "*")
public class SpotifyController {
    
    private Connection connection;
    private String clientId;
    private String clientSecret;
    private SpotifyService spotifyService;
    
    public SpotifyController() {
        try {
            this.connection = DBInitializer.getConnection();

            // Priority 1: Railway environment variables
            this.clientId = System.getenv("SPOTIFY_CLIENT_ID");
            this.clientSecret = System.getenv("SPOTIFY_CLIENT_SECRET");

            // Priority 2: .env file fallback (local dev only)
            if (this.clientId == null || this.clientSecret == null) {
                System.out.println("Trying .env file...");
                try {
                    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                    this.clientId = dotenv.get("SPOTIFY_CLIENT_ID");
                    this.clientSecret = dotenv.get("SPOTIFY_CLIENT_SECRET");
                } catch (Throwable e) {
                    // It's better to log this, but for now we'll leave it
                    //this should cause it to change
                }
            }

            if (this.clientId == null || this.clientSecret == null) {
                throw new RuntimeException("Spotify credentials not found! Set SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET as Railway environment variables.");
            }

            System.out.println("Spotify credentials loaded");
            this.spotifyService = new SpotifyService(this.clientId, this.clientSecret);
            createSongsTableIfNotExists();
            

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize: " + e.getMessage(), e);
        }
    }
    
    // Create songs table if it doesn't exist
    private void createSongsTableIfNotExists() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS spotify_songs ("
                + "id TEXT PRIMARY KEY, "
                + "box_id TEXT, "
                + "spotify_id TEXT, "
                + "name TEXT, "
                + "artist TEXT, "
                + "album TEXT, "
                + "album_cover_url TEXT, "
                + "preview_url TEXT, "
                + "spotify_url TEXT, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Spotify songs table created or already exists");
        }
    }
    
    /**
     * Search for songs on Spotify
     * GET /api/spotify/search?query=songname&limit=10
     */
    @GetMapping("/search")
    public ResponseEntity<String> searchSongs(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        try {
            JsonArray results = spotifyService.searchSongs(query, limit);
            return ResponseEntity.ok(results.toString());
        } catch (IOException e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to search songs: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get song details by Spotify ID
     * GET /api/spotify/song/{spotifyId}
     */
    @GetMapping("/song/{spotifyId}")
    public ResponseEntity<String> getSongById(@PathVariable String spotifyId) {
        try {
            JsonObject song = spotifyService.getSongById(spotifyId);
            return ResponseEntity.ok(song.toString());
        } catch (IOException e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to get song: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Save a song to a box
     * POST /api/spotify/save
     * Body: { "boxId": "box1", "spotifyId": "3n3Ppam7vgaVa1iaRUc9Lp" }
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveSongToBox(@RequestBody String requestBodyJson) {
        try {
            // Parse the JSON request body
            com.google.gson.JsonParser parser = new com.google.gson.JsonParser();
            JsonObject requestBody = parser.parse(requestBodyJson).getAsJsonObject();
            
            String boxId = requestBody.get("boxId").getAsString();
            String spotifyId = requestBody.get("spotifyId").getAsString();
            
            // Get song details from Spotify
            JsonObject song = spotifyService.getSongById(spotifyId);
            
            // Extract song information
            String name = song.get("name").getAsString();
            String artist = song.getAsJsonArray("artists")
                .get(0).getAsJsonObject()
                .get("name").getAsString();
            String album = song.getAsJsonObject("album")
                .get("name").getAsString();
            String albumCoverUrl = song.getAsJsonObject("album")
                .getAsJsonArray("images")
                .get(0).getAsJsonObject()
                .get("url").getAsString();
            String previewUrl = song.has("preview_url") && !song.get("preview_url").isJsonNull()
                ? song.get("preview_url").getAsString()
                : null;
            String spotifyUrl = song.getAsJsonObject("external_urls")
                .get("spotify").getAsString();
            
            // Save to database
            String insertSQL = """
                INSERT INTO spotify_songs (box_id, spotify_id, name, artist, album, album_cover_url, preview_url, spotify_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
                pstmt.setString(1, boxId);
                pstmt.setString(2, spotifyId);
                pstmt.setString(3, name);
                pstmt.setString(4, artist);
                pstmt.setString(5, album);
                pstmt.setString(6, albumCoverUrl);
                pstmt.setString(7, previewUrl);
                pstmt.setString(8, spotifyUrl);
                pstmt.executeUpdate();
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("message", "Song saved successfully");
            response.addProperty("boxId", boxId);
            response.addProperty("spotifyId", spotifyId);
            response.addProperty("name", name);
            response.addProperty("artist", artist);
            
            return ResponseEntity.ok(response.toString());
            
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to save song: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get all songs for a specific box
     * GET /api/spotify/box/{boxId}
     */
    @GetMapping("/box/{boxId}")
    public ResponseEntity<String> getSongsByBox(@PathVariable String boxId) {
        try {
            String selectSQL = "SELECT * FROM spotify_songs WHERE box_id = ? ORDER BY created_at DESC";
            JsonArray songsArray = new JsonArray();
            
            try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
                pstmt.setString(1, boxId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    JsonObject song = new JsonObject();
                    song.addProperty("id", rs.getInt("id"));
                    song.addProperty("boxId", rs.getString("box_id"));
                    song.addProperty("spotifyId", rs.getString("spotify_id"));
                    song.addProperty("name", rs.getString("name"));
                    song.addProperty("artist", rs.getString("artist"));
                    song.addProperty("album", rs.getString("album"));
                    song.addProperty("albumCoverUrl", rs.getString("album_cover_url"));
                    song.addProperty("previewUrl", rs.getString("preview_url"));
                    song.addProperty("spotifyUrl", rs.getString("spotify_url"));
                    song.addProperty("createdAt", rs.getString("created_at"));
                    
                    songsArray.add(song);
                }
            }
            
            return ResponseEntity.ok(songsArray.toString());
            
        } catch (SQLException e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to get songs: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Delete a song from a box
     * DELETE /api/spotify/song/{songId}
     */
    @DeleteMapping("/song/{songId}")
    public ResponseEntity<String> deleteSong(@PathVariable int songId) {
        try {
            String deleteSQL = "DELETE FROM spotify_songs WHERE id = ?";
            
            try (PreparedStatement pstmt = connection.prepareStatement(deleteSQL)) {
                pstmt.setInt(1, songId);
                int rowsAffected = pstmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    JsonObject response = new JsonObject();
                    response.addProperty("message", "Song deleted successfully");
                    response.addProperty("songId", songId);
                    return ResponseEntity.ok(response.toString());
                } else {
                    return ResponseEntity.status(404)
                        .body("{\"error\": \"Song not found\"}");
                }
            }
            
        } catch (SQLException e) {
            return ResponseEntity.status(500)
                .body("{\"error\": \"Failed to delete song: " + e.getMessage() + "\"}");
        }
    }
}
