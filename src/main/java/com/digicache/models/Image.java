package com.digicache.models;

import java.time.LocalDateTime;

public class Image {
    private String id;
    private String boxId;
    private byte[] data;
    private String contentType;
    private LocalDateTime createdAt;

    public Image() {
        this.createdAt = LocalDateTime.now();
    }

    public Image(String id, String boxId, byte[] data, String contentType) {
        this.id = id;
        this.boxId = boxId;
        this.data = data;
        this.contentType = contentType;
        this.createdAt = LocalDateTime.now();
    }

    public Image(String id, String boxId, byte[] data, String contentType, LocalDateTime createdAt) {
        this.id = id;
        this.boxId = boxId;
        this.data = data;
        this.contentType = contentType;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBoxId() {
        return boxId;
    }

    public void setBoxId(String boxId) {
        this.boxId = boxId;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
