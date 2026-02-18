package com.digicache.services;

import java.io.IOException;
import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SpotifyService {
    
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search";
    
    private final String clientId;
    private final String clientSecret;
    private final OkHttpClient client;
    private String accessToken;
    
    public SpotifyService(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.client = new OkHttpClient();
    }
    
    // generate token
    public void authenticate() throws IOException {
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        
        RequestBody body = new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build();
        
        Request request = new Request.Builder()
            .url(TOKEN_URL)
            .addHeader("Authorization", "Basic " + encodedCredentials)
            .post(body)
            .build();
        
        // receive response
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("could not generate access token: " + response);
            }
            
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            this.accessToken = json.get("access_token").getAsString();
            System.out.println("successfully generated access token");
        }
    }
    
    // search up songs
    public JsonArray searchSongs(String query, int limit) throws IOException {
        if (accessToken == null) {
            authenticate();
        }
        
        HttpUrl url = HttpUrl.parse(SEARCH_URL).newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", String.valueOf(limit))
            .build();
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            // If token expired (401), re-authenticate and retry
            if (response.code() == 401) {
                System.out.println("Access token expired, re-authenticating...");
                authenticate();
                
                // Retry the request with new token
                Request retryRequest = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();
                
                try (Response retryResponse = client.newCall(retryRequest).execute()) {
                    if (!retryResponse.isSuccessful()) {
                        throw new IOException("search failed after re-authentication: " + retryResponse);
                    }
                    
                    String responseBody = retryResponse.body().string();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    return json.getAsJsonObject("tracks").getAsJsonArray("items");
                }
            }
            
            if (!response.isSuccessful()) {
                throw new IOException("search failed: " + response);
            }
            
            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return json.getAsJsonObject("tracks").getAsJsonArray("items");
        }
    }
    
    // song details using the song's Spotify ID
    public JsonObject getSongById(String spotifyId) throws IOException {
        if (accessToken == null) {
            authenticate();
        }
        
        String url = "https://api.spotify.com/v1/tracks/" + spotifyId;
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + accessToken)
            .get()
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            // If token expired (401), re-authenticate and retry
            if (response.code() == 401) {
                System.out.println("Access token expired, re-authenticating...");
                authenticate();
                
                // Retry the request with new token
                Request retryRequest = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .get()
                    .build();
                
                try (Response retryResponse = client.newCall(retryRequest).execute()) {
                    if (!retryResponse.isSuccessful()) {
                        throw new IOException("getting song by id failed after re-authentication: " + retryResponse);
                    }
                    
                    String responseBody = retryResponse.body().string();
                    return JsonParser.parseString(responseBody).getAsJsonObject();
                }
            }
            
            if (!response.isSuccessful()) {
                throw new IOException("getting song by id failed: " + response);
            }
            
            String responseBody = response.body().string();
            return JsonParser.parseString(responseBody).getAsJsonObject();
        }
    }
    
    // for song info (lowkey just for testing)
    public void displaySong(JsonObject track) {
        String name = track.get("name").getAsString();
        String artist = track.getAsJsonArray("artists")
            .get(0).getAsJsonObject()
            .get("name").getAsString();
        String album = track.getAsJsonObject("album")
            .get("name").getAsString();
        String albumCover = track.getAsJsonObject("album")
            .getAsJsonArray("images")
            .get(0).getAsJsonObject()
            .get("url").getAsString();
        // does hyperlink refer to the uri or spotify url?
        // can potentially add a url for an audio preview but this is kind of weird rn
        // YES ADD THE HYPERLINK FOR SPOTIFY SONG
        
        System.out.println("Song: " + name);
        System.out.println("Artist: " + artist);
        System.out.println("Album: " + album);
        System.out.println("Album Cover URL: " + albumCover);
        System.out.println("---");
    }
}