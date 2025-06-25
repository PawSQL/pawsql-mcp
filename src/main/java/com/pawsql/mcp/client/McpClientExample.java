package com.pawsql.mcp.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Example client that demonstrates how to use the McpClientAuthenticator
 * to connect to the MCP server with just URL, username, and password.
 */
public class McpClientExample {
    
    public static void main(String[] args) {
        try {
            // User configuration - this is all the user needs to provide
            String serverUrl = "http://localhost:8080";
            String email = "user@example.com";
            String password = "password123";
            String edition = "cloud";
            
            // Get authenticated URL with automatic token handling
            String authenticatedUrl = McpClientAuthenticator.getAuthenticatedUrl(
                    serverUrl, email, password, edition);
            
            System.out.println("Connecting to MCP server with authenticated URL: " + authenticatedUrl);
            
            // Example of connecting to the SSE endpoint
            connectToSseEndpoint(authenticatedUrl);
            
        } catch (Exception e) {
            System.err.println("Error connecting to MCP server: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example method to connect to the SSE endpoint.
     * In a real client, this would be replaced with proper SSE client code.
     * 
     * @param authenticatedUrl The authenticated URL to connect to
     */
    private static void connectToSseEndpoint(String authenticatedUrl) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authenticatedUrl))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        
        System.out.println("Sending request to SSE endpoint...");
        
        // In a real client, you would use a proper SSE client library
        // This is just a simple example to demonstrate the authentication flow
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("Response status code: " + response.statusCode());
        System.out.println("Response headers: " + response.headers());
        System.out.println("Response body: " + response.body());
    }
}
