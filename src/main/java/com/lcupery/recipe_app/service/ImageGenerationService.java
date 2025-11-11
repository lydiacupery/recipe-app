package com.lcupery.recipe_app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ImageGenerationService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final VercelBlobService vercelBlobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImageGenerationService(VercelBlobService vercelBlobService) {
        this.vercelBlobService = vercelBlobService;
    }

    public String generateRecipeImage(String recipeName, String description, String ingredients, String steps) throws IOException {
        // Create a prompt for DALL-E
        String prompt = buildImagePrompt(recipeName, description, ingredients, steps);

        log.info("Generating image for recipe: {} with prompt: {}", recipeName, prompt);

        // Call OpenAI DALL-E API
        String imageUrl = callDallEApi(prompt);

        log.info("Image generated at temporary URL: {}", imageUrl);

        // Download the image from OpenAI
        byte[] imageBytes = downloadImage(imageUrl);

        // Upload to Vercel Blob
        String permanentUrl = uploadToVercelBlob(imageBytes, recipeName);

        log.info("Image uploaded to Vercel Blob: {}", permanentUrl);

        return permanentUrl;
    }

    private String buildImagePrompt(String recipeName, String description, String ingredients, String steps) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("A cartoon-style, colorful illustration of ");
        prompt.append(recipeName);

        if (description != null && !description.isEmpty()) {
            prompt.append(". ");
            // Take first 100 chars of description
            String shortDesc = description.length() > 100
                ? description.substring(0, 100)
                : description;
            prompt.append(shortDesc);
        }

        // Add key ingredients for context
        if (ingredients != null && !ingredients.isEmpty()) {
            // Take first 150 chars of ingredients
            String shortIngredients = ingredients.length() > 150
                ? ingredients.substring(0, 150) + "..."
                : ingredients;
            prompt.append(" Made with: ").append(shortIngredients).append(".");
        }

        // Add cooking method context from steps
        if (steps != null && !steps.isEmpty()) {
            // Extract cooking method keywords from steps
            String lowerSteps = steps.toLowerCase();
            if (lowerSteps.contains("bake") || lowerSteps.contains("oven")) {
                prompt.append(" Baked to perfection.");
            } else if (lowerSteps.contains("grill")) {
                prompt.append(" Grilled with beautiful char marks.");
            } else if (lowerSteps.contains("fry") || lowerSteps.contains("pan")) {
                prompt.append(" Pan-fried until golden.");
            } else if (lowerSteps.contains("boil") || lowerSteps.contains("simmer")) {
                prompt.append(" Simmered in a pot.");
            }
        }

        prompt.append(" The image should look appetizing and fun, ");
        prompt.append("with a whimsical, cartoon art style. ");
        prompt.append("Food should be the main focus, beautifully plated on a dish.");

        return prompt.toString();
    }

    private String callDallEApi(String prompt) throws IOException {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + openaiApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "dall-e-3");
        requestBody.put("prompt", prompt);
        requestBody.put("n", 1);
        requestBody.put("size", "1024x1024");
        requestBody.put("quality", "standard");
        requestBody.put("style", "vivid");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                "https://api.openai.com/v1/images/generations",
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String imageUrl = root.path("data").get(0).path("url").asText();
                return imageUrl;
            } else {
                throw new RuntimeException("Failed to generate image with DALL-E");
            }
        } catch (Exception e) {
            log.error("Error calling DALL-E API", e);
            throw new RuntimeException("Failed to generate image: " + e.getMessage(), e);
        }
    }

    private byte[] downloadImage(String imageUrl) throws IOException {
        try {
            URL url = new URL(imageUrl);
            try (InputStream in = url.openStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.error("Error downloading generated image", e);
            throw new RuntimeException("Failed to download generated image: " + e.getMessage(), e);
        }
    }

    private String uploadToVercelBlob(byte[] imageBytes, String recipeName) throws IOException {
        // Create a MultipartFile wrapper for the byte array
        MultipartFile multipartFile = new ByteArrayMultipartFile(
            imageBytes,
            "generated-" + recipeName.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase() + ".png",
            "image/png"
        );

        return vercelBlobService.uploadImage(multipartFile);
    }

    // Helper class to wrap byte array as MultipartFile
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String name, String contentType) {
            this.content = content;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}
