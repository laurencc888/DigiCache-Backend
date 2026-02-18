package com.digicache.services;

import com.digicache.models.Image;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID; // used to generate ids
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ImageService {
    
    private final Connection connection;
    private static final String IMAGE_STORAGE_PATH = "data/images/"; // This should be a path to a persistent volume
    
    public ImageService(Connection connection) {
        this.connection = connection;
        // Ensure the storage directory exists
        try {
            Files.createDirectories(Paths.get(IMAGE_STORAGE_PATH));
        } catch (IOException e) {
            throw new RuntimeException("Could not create image storage directory", e);
        }
    }
    
    // stores image and returns its id
    public String storeImage(String imagePath, String boxId) throws SQLException, IOException {
        File imageFile = new File(imagePath);
        
        if (!imageFile.exists()) {
            throw new IOException("image not found: " + imagePath);
        }
        
        String imageId = UUID.randomUUID().toString();
        String fileExtension = getFileExtension(imagePath);
        String newFileName = imageId + fileExtension;
        Path destinationPath = Paths.get(IMAGE_STORAGE_PATH, newFileName);

        // Copy the file to the storage directory
        Files.copy(imageFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        
        // adding to the db
        String insertContent = "INSERT INTO box_contents (box_id, item_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertContent)) {
            stmt.setString(1, boxId);
            stmt.setString(2, imageId);
            stmt.executeUpdate();
        }
        
        String insertImage = "INSERT INTO images (id, box_id, image_path, content_type, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertImage)) {
            stmt.setString(1, imageId);
            stmt.setString(2, boxId); // Make sure boxId is set
            stmt.setString(3, destinationPath.toString());
            stmt.setString(4, "image/jpeg");
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            stmt.executeUpdate();
        }
        return imageId;
    }
    
    // getting image from db
    public void getImage(String imageId, String outputPath) throws SQLException, IOException {
        String query = "SELECT image_path FROM images WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, imageId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String imagePath = rs.getString("image_path");
                    Path sourcePath = Paths.get(imagePath);

                    if (Files.exists(sourcePath)) {
                        Files.copy(sourcePath, Paths.get(outputPath), StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("saved image to: " + outputPath);
                    } else {
                        throw new IOException("Image file not found at: " + imagePath);
                    }
                } 
                else {
                    System.out.println("image could not be found: " + imageId);
                }
            }
        }
    }
    
    // highkey this can be moved into its own class
    public void createBox(String boxId) throws SQLException {
        String insert = "INSERT INTO box_ids (id) VALUES (?)";
        try (PreparedStatement stmt = connection.prepareStatement(insert)) {
            stmt.setString(1, boxId);
            stmt.executeUpdate();
            System.out.println("Box created: " + boxId);
        }
    }
    
    // Store background image for a box (replaces existing if any)
    public void storeBackgroundImage(String imagePath, String boxId) throws SQLException, IOException {
        File imageFile = new File(imagePath);
        
        if (!imageFile.exists()) {
            throw new IOException("image not found: " + imagePath);
        }
        
        String fileExtension = getFileExtension(imagePath);
        String newFileName = "bg_" + boxId + fileExtension;
        Path destinationPath = Paths.get(IMAGE_STORAGE_PATH, newFileName);

        // Copy the file to the storage directory
        Files.copy(imageFile.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Insert or replace background image for this box
        String insertBackground = "INSERT OR REPLACE INTO background_images (box_id, image_path) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertBackground)) {
            stmt.setString(1, boxId);
            stmt.setString(2, destinationPath.toString());
            stmt.executeUpdate();
        }
        
        System.out.println("Background image stored for box: " + boxId);
    }
    
    // Get background image for a box
    public byte[] getBackgroundImage(String boxId) throws SQLException, IOException {
        String query = "SELECT image_path FROM background_images WHERE box_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, boxId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String imagePath = rs.getString("image_path");
                    if (imagePath != null && !imagePath.isEmpty()) {
                        return Files.readAllBytes(Paths.get(imagePath));
                    }
                }
            }
        }
        return null;
    }
    
    public String saveImage(byte[] data, String boxId, String contentType) throws SQLException, IOException {
        String imageId = UUID.randomUUID().toString();
        String fileExtension = getFileExtensionForContentType(contentType);
        String newFileName = imageId + fileExtension;
        Path destinationPath = Paths.get(IMAGE_STORAGE_PATH, newFileName);

        // Write byte data to a file
        Files.write(destinationPath, data);

        String sql = "INSERT INTO images (id, box_id, image_path, content_type, created_at) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, imageId);
            statement.setString(2, boxId);
            statement.setString(3, destinationPath.toString());
            statement.setString(4, contentType);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
        
        // Add image ID to box's image list
        String updateBox = "UPDATE box_ids SET image_ids = COALESCE(image_ids, '') || ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateBox)) {
            stmt.setString(1, imageId + ",");
            stmt.setString(2, boxId);
            stmt.executeUpdate();
        }
        
        return imageId;
    }
    
    public Image getImageById(String id) throws SQLException, IOException {
        String sql = "SELECT id, box_id, image_path, content_type, created_at FROM images WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                Image image = new Image();
                image.setId(rs.getString("id"));
                image.setBoxId(rs.getString("box_id"));
                
                String imagePath = rs.getString("image_path");
                if (imagePath != null && !imagePath.isEmpty() && Files.exists(Paths.get(imagePath))) {
                    image.setData(Files.readAllBytes(Paths.get(imagePath)));
                }
                
                // Get content type if stored, otherwise default to jpeg
                String contentType = rs.getString("content_type");
                image.setContentType(contentType != null ? contentType : "image/jpeg");
                
                // Get timestamp
                Timestamp timestamp = rs.getTimestamp("created_at");
                if (timestamp != null) {
                    image.setCreatedAt(timestamp.toLocalDateTime());
                } else {
                    image.setCreatedAt(LocalDateTime.now());
                }
                
                return image;
            }
        }
        
        return null;
    }

    private String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return fileName.substring(lastIndexOf);
    }

    private String getFileExtensionForContentType(String contentType) {
        if (contentType != null) {
            switch (contentType.toLowerCase()) {
                case "image/jpeg":
                    return ".jpg";
                case "image/png":
                    return ".png";
                case "image/gif":
                    return ".gif";
                // Add other content types as needed
            }
        }
        return ".jpg"; // Default extension
    }
}