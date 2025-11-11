package com.lcupery.recipe_app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
public class VercelBlobService {

    @Value("${vercel.blob.token}")
    private String blobToken;

    private static final String VERCEL_BLOB_API_URL = "https://blob.vercel-storage.com";

    public String uploadImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }

        RestTemplate restTemplate = new RestTemplate();

        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + blobToken);
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.set("x-content-type", contentType);

        // Get original filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            filename = "recipe-image-" + System.currentTimeMillis() + ".jpg";
        }

        // Upload to Vercel Blob
        String uploadUrl = VERCEL_BLOB_API_URL + "/" + filename;

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.PUT,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String url = (String) responseBody.get("url");
                log.info("Image uploaded successfully to Vercel Blob: {}", url);
                return url;
            } else {
                throw new RuntimeException("Failed to upload image to Vercel Blob");
            }
        } catch (Exception e) {
            log.error("Error uploading image to Vercel Blob", e);
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
    }
}
