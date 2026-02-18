package com.digicache.controllers;

import com.digicache.models.Image;
import com.digicache.services.DBInitializer;
import com.digicache.services.ImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class ImageController {
    
    private Connection connection;
    private ImageService imageService;
    
    public ImageController() {
        try {
            this.connection = DBInitializer.getConnection();
            if (this.connection == null) {
                throw new SQLException("Database connection is null");
            }
            this.imageService = new ImageService(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize ImageController: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create a new box
     * POST /api/images/boxes
     * Body: { "boxId": "box1" }
     */
    @PostMapping("/boxes")
    public ResponseEntity<String> createBox(@RequestBody String requestBody) {
        try {
            // Parse the JSON string manually
            com.google.gson.Gson gson = new com.google.gson.Gson();
            JsonObject jsonObject = gson.fromJson(requestBody, JsonObject.class);
            
            if (jsonObject == null || !jsonObject.has("boxId")) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing 'boxId' field in request body");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.toString());
            }
            
            String boxId = jsonObject.get("boxId").getAsString();
            imageService.createBox(boxId);
            
            JsonObject response = new JsonObject();
            response.addProperty("message", "Box created successfully");
            response.addProperty("boxId", boxId);
            
            return ResponseEntity.ok(response.toString());
        } catch (SQLException e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to create box: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error.toString());
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.toString());
        }
    }
    
    /**
     * Get all boxes with their image IDs
     * GET /api/images/boxes
     */
    @GetMapping("/boxes")
    public ResponseEntity<String> getAllBoxes() {
        try {
            JsonArray boxesArray = new JsonArray();
            
            // Get all box IDs
            String boxQuery = "SELECT id FROM box_ids";
            try (PreparedStatement boxStmt = connection.prepareStatement(boxQuery);
                 ResultSet boxRs = boxStmt.executeQuery()) {
                
                while (boxRs.next()) {
                    String boxId = boxRs.getString("id");
                    JsonObject boxObj = new JsonObject();
                    boxObj.addProperty("boxId", boxId);
                    
                    // Get image IDs for this box
                    JsonArray imagesArray = new JsonArray();
                    String contentQuery = "SELECT item_id FROM box_contents WHERE box_id = ? AND item_id IN (SELECT id FROM images)";
                    try (PreparedStatement contentStmt = connection.prepareStatement(contentQuery)) {
                        contentStmt.setString(1, boxId);
                        try (ResultSet contentRs = contentStmt.executeQuery()) {
                            while (contentRs.next()) {
                                imagesArray.add(contentRs.getString("item_id"));
                            }
                        }
                    }
                    
                    boxObj.add("images", imagesArray);
                    boxesArray.add(boxObj);
                }
            }
            
            return ResponseEntity.ok(boxesArray.toString());
        } catch (SQLException e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to get boxes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error.toString());
        }
    }
    
    /**
     * Upload an image to a box
     * POST /api/images/upload
     * Form data: file (multipart), boxId (string), type (string)
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("boxId") String boxId,
            @RequestParam(value = "type", required = false) String type) {

        try {
            // Save the uploaded file temporarily
            File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }

            // Try to detect MIME type from the multipart content-type first
            String detectedMime = file.getContentType();
            if (detectedMime == null || detectedMime.isBlank()) {
                // fallback to probing the temp file
                try {
                    detectedMime = Files.probeContentType(tempFile.toPath());
                } catch (IOException ignored) { }
            }

            // final fallback: infer from filename extension
            if ((detectedMime == null || detectedMime.isBlank()) && file.getOriginalFilename() != null) {
                String lower = file.getOriginalFilename().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".png")) detectedMime = "image/png";
                else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) detectedMime = "image/jpeg";
                else if (lower.endsWith(".gif")) detectedMime = "image/gif";
                else if (lower.endsWith(".txt")) detectedMime = "text/plain";
            }

            System.out.println("Detected MIME type: " + detectedMime + " (client type param: " + type + ")");

            // If detected as image, treat it as image regardless of the provided 'type' param
            if (detectedMime != null && detectedMime.startsWith("image/")) {
                String imageId = imageService.storeImage(tempFile.getAbsolutePath(), boxId);

                // attempt to delete temp file
                if (!tempFile.delete()) {
                    System.out.println("Warning: Could not delete temporary file: " + tempFile.getAbsolutePath());
                }

                JsonObject response = new JsonObject();
                response.addProperty("message", "Image uploaded successfully");
                response.addProperty("imageId", imageId);
                response.addProperty("boxId", boxId);
                response.addProperty("detectedMime", detectedMime);

                return ResponseEntity.ok(response.toString());
            }

            // If the file is clearly text, instruct caller to use the text endpoint
            if (detectedMime != null && detectedMime.startsWith("text")) {
                JsonObject error = new JsonObject();
                error.addProperty("error", "Uploaded file is a text file. Use /api/texts/upload for text uploads.");
                error.addProperty("detectedMime", detectedMime);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.toString());
            }

            // Unsupported media type
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unsupported file type. Expected an image (png/jpg/gif).");
            error.addProperty("detectedMime", detectedMime);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error.toString());

        } catch (Exception e) {
            System.err.println("Error during image upload: " + e.getMessage());
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error.toString());
        }
    }
    
    /**
     * Get an image by ID
     * GET /api/images/{imageId}
     */
    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> getImage(@PathVariable String imageId) {
        try {
            String query = "SELECT image_data FROM images WHERE id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, imageId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        byte[] imageData = rs.getBytes("image_data");
                        return ResponseEntity.ok()
                            .contentType(MediaType.IMAGE_JPEG)
                            .body(imageData);
                    } else {
                        return ResponseEntity.notFound().build();
                    }
                }
            }
            
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Upload a background image for a box (separate from tile images)
     * POST /api/images/background/upload
     * Form data: file (multipart), boxId (string)
     */
    @PostMapping("/background/upload")
    public ResponseEntity<String> uploadBackgroundImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("boxId") String boxId) {
        
        try {
            System.out.println("Processing background image upload for box: " + boxId);
            
            // Save the uploaded file temporarily
            File tempFile = File.createTempFile("background-", file.getOriginalFilename());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
                System.out.println("Temporary file created: " + tempFile.getAbsolutePath());
            }
            
            // Store as background image (not a tile)
            imageService.storeBackgroundImage(tempFile.getAbsolutePath(), boxId);
            System.out.println("Background image stored successfully for box: " + boxId);
            
            // Delete temp file
            if (!tempFile.delete()) {
                System.out.println("Warning: Could not delete temporary file: " + tempFile.getAbsolutePath());
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("message", "Background image uploaded successfully");
            response.addProperty("boxId", boxId);
            
            return ResponseEntity.ok(response.toString());
            
        } catch (Exception e) {
            System.err.println("Error during background image upload: " + e.getMessage());
            e.printStackTrace();
            JsonObject error = new JsonObject();
            error.addProperty("error", "Failed to upload background image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error.toString());
        }
    }
    
    /**
     * Get background image for a box
     * GET /api/images/background/{boxId}
     */
    @GetMapping("/background/{boxId}")
    public ResponseEntity<byte[]> getBackgroundImage(@PathVariable String boxId) {
        try {
            byte[] imageData = imageService.getBackgroundImage(boxId);
            
            if (imageData != null) {
                return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(imageData);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (SQLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get metadata for an image by ID
     * GET /api/images/{id}/metadata
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getImageMetadata(@PathVariable String id) {
        try {
            Image image = imageService.getImageById(id);
            if (image == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", image.getId());
            metadata.put("boxId", image.getBoxId());
            metadata.put("contentType", image.getContentType());
            metadata.put("createdAt", image.getCreatedAt().toString());
            
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}
