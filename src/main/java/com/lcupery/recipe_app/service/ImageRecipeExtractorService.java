package com.lcupery.recipe_app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.entity.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

@Slf4j
@Service
public class ImageRecipeExtractorService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeDto extractRecipeFromImage(MultipartFile imageFile, String citation) throws IOException, InterruptedException {
        if (imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }

        // Validate file type
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("File must be an image");
        }

        log.info("Extracting recipe from image: {}, size: {} bytes", imageFile.getOriginalFilename(), imageFile.getSize());

        // Convert image to base64
        byte[] imageBytes = imageFile.getBytes();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String imageDataUrl = "data:" + contentType + ";base64," + base64Image;

        // Call OpenAI Vision API
        RecipeDto recipe = callVisionAPI(imageDataUrl);
        recipe.setSourceType(SourceType.TEXT);

        // Use citation if provided, otherwise use filename
        if (citation != null && !citation.trim().isEmpty()) {
            recipe.setSourceValue(citation);
        } else {
            recipe.setSourceValue("Extracted from image: " + imageFile.getOriginalFilename());
        }

        log.info("Successfully extracted recipe '{}' from image", recipe.getName());
        return recipe;
    }

    private RecipeDto callVisionAPI(String imageDataUrl) throws IOException, InterruptedException {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        String prompt = "Please analyze this recipe image and extract all recipe information. " +
                "Return the information as JSON with this exact structure:\n" +
                "{\n" +
                "  \"name\": \"recipe name\",\n" +
                "  \"description\": \"brief description of the dish\",\n" +
                "  \"prepTime\": \"preparation time if mentioned (e.g., '15 minutes')\",\n" +
                "  \"cookTime\": \"cooking time if mentioned (e.g., '30 minutes')\",\n" +
                "  \"servings\": \"number of servings if mentioned (e.g., '4 servings')\",\n" +
                "  \"category\": \"recipe category if clear (e.g., 'Appetizer', 'Main Course', 'Dessert')\",\n" +
                "  \"ingredients\": [{\"name\": \"ingredient name\", \"quantity\": \"amount with unit\"}],\n" +
                "  \"steps\": [{\"stepNumber\": 1, \"instruction\": \"step instruction\"}]\n" +
                "}\n\n" +
                "IMPORTANT RULES:\n" +
                "1. Extract ONLY what you can clearly read in the image\n" +
                "2. Do not infer or add information that isn't visible\n" +
                "3. If text is unclear or cut off, skip that part\n" +
                "4. Preserve exact wording from the image\n" +
                "5. For steps/instructions:\n" +
                "   - If there are numbered steps, extract each as a separate step\n" +
                "   - If there is instructional text but NOT numbered (e.g., a paragraph), put ALL instruction text as a SINGLE step with stepNumber 1\n" +
                "   - If there are NO instructions at all, use empty array\n" +
                "6. Description should be a brief summary of the dish, NOT the instructions\n" +
                "7. Return ONLY valid JSON, no other text";

        String requestBody = String.format(
                "{" +
                "  \"model\": \"gpt-4o-mini\"," +
                "  \"messages\": [" +
                "    {" +
                "      \"role\": \"user\"," +
                "      \"content\": [" +
                "        {\"type\": \"text\", \"text\": %s}," +
                "        {\"type\": \"image_url\", \"image_url\": {\"url\": %s}}" +
                "      ]" +
                "    }" +
                "  ]," +
                "  \"max_tokens\": 1500," +
                "  \"temperature\": 0.1" +
                "}",
                objectMapper.writeValueAsString(prompt),
                objectMapper.writeValueAsString(imageDataUrl)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.debug("Calling OpenAI Vision API...");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("OpenAI Vision API error: Status {}, Body: {}", response.statusCode(), response.body());
            throw new IOException("OpenAI Vision API request failed. Status: " + response.statusCode());
        }

        JsonNode responseNode = objectMapper.readTree(response.body());
        String recipeJson = responseNode
                .get("choices")
                .get(0)
                .get("message")
                .get("content")
                .asText();

        log.debug("OpenAI Vision response: {}", recipeJson);

        // Clean up potential markdown code blocks
        recipeJson = recipeJson.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

        // Parse the JSON response
        RecipeDto recipe = objectMapper.readValue(recipeJson, RecipeDto.class);
        return recipe;
    }
}
