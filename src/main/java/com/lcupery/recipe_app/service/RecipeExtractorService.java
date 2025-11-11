package com.lcupery.recipe_app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lcupery.recipe_app.dto.IngredientDto;
import com.lcupery.recipe_app.dto.RecipeDto;
import com.lcupery.recipe_app.dto.StepDto;
import com.lcupery.recipe_app.entity.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RecipeExtractorService {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract recipe from URL using hybrid approach:
     * 1. Try schema.org extraction first
     * 2. Fall back to LLM if schema.org fails
     */
    public RecipeDto extractRecipeFromUrl(String url) throws IOException, InterruptedException {
        log.info("Starting recipe extraction from URL: {}", url);

        // Fetch the webpage content
        String htmlContent = fetchWebpage(url);

        // Try schema.org first
        try {
            RecipeDto recipe = extractFromSchemaOrg(htmlContent, url);
            if (recipe != null) {
                log.info("✓ Successfully extracted recipe using Schema.org approach");
                return recipe;
            }
        } catch (Exception e) {
            log.debug("Schema.org extraction failed: {}", e.getMessage());
        }

        // Fall back to LLM
        log.info("Schema.org extraction failed, falling back to LLM...");
        RecipeDto recipe = extractUsingLLM(htmlContent, url);
        log.info("✓ Successfully extracted recipe using LLM approach");
        return recipe;
    }

    private String fetchWebpage(String url) throws IOException, InterruptedException {
        log.debug("Fetching webpage content from: {}", url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Recipe App)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch URL. Status code: " + response.statusCode());
        }
        return response.body();
    }

    private RecipeDto extractFromSchemaOrg(String htmlContent, String url) throws Exception {
        log.debug("Attempting schema.org extraction...");

        // Pattern to find JSON-LD scripts
        Pattern pattern = Pattern.compile(
                "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(htmlContent);

        int scriptCount = 0;
        while (matcher.find()) {
            scriptCount++;
            String jsonContent = matcher.group(1).trim();
            log.debug("Found JSON-LD script block #{}", scriptCount);

            try {
                JsonNode rootNode = objectMapper.readTree(jsonContent);

                // Handle both single objects and arrays
                JsonNode recipeNode = findRecipeNode(rootNode);
                if (recipeNode != null) {
                    log.debug("Found Recipe node in JSON-LD block #{}", scriptCount);
                    return parseSchemaOrgRecipe(recipeNode, url);
                } else {
                    log.debug("No Recipe node found in JSON-LD block #{}", scriptCount);
                }
            } catch (Exception e) {
                log.debug("Failed to parse JSON-LD block #{}: {}", scriptCount, e.getMessage());
            }
        }

        log.debug("Processed {} JSON-LD script blocks, no Recipe found", scriptCount);
        return null;
    }

    private JsonNode findRecipeNode(JsonNode node) {
        // Check if current node is a Recipe
        if (node.has("@type")) {
            JsonNode typeNode = node.get("@type");

            // @type can be a string or an array
            if (typeNode.isArray()) {
                for (JsonNode typeElement : typeNode) {
                    if ("Recipe".equals(typeElement.asText())) {
                        log.debug("Found Recipe in @type array");
                        return node;
                    }
                }
            } else {
                String type = typeNode.asText();
                if ("Recipe".equals(type)) {
                    log.debug("Found Recipe with @type string");
                    return node;
                }
            }
        }

        // Check @graph property first (common in schema.org)
        if (node.has("@graph")) {
            log.debug("Searching in @graph array");
            return findRecipeNode(node.get("@graph"));
        }

        // If it's an array, search through elements
        if (node.isArray()) {
            log.debug("Searching in array with {} elements", node.size());
            for (JsonNode element : node) {
                JsonNode found = findRecipeNode(element);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private RecipeDto parseSchemaOrgRecipe(JsonNode recipeNode, String url) {
        RecipeDto recipe = new RecipeDto();

        // Extract name
        if (recipeNode.has("name")) {
            recipe.setName(recipeNode.get("name").asText());
            log.debug("Extracted recipe name: {}", recipe.getName());
        }

        // Extract description
        if (recipeNode.has("description")) {
            recipe.setDescription(recipeNode.get("description").asText());
            log.debug("Extracted recipe description: {}", recipe.getDescription());
        }

        // Set source
        recipe.setSourceType(SourceType.URL);
        recipe.setSourceValue(url);

        // Extract ingredients
        List<IngredientDto> ingredients = new ArrayList<>();
        if (recipeNode.has("recipeIngredient")) {
            JsonNode ingredientsNode = recipeNode.get("recipeIngredient");
            log.debug("Found recipeIngredient node, isArray: {}, size: {}",
                ingredientsNode.isArray(),
                ingredientsNode.isArray() ? ingredientsNode.size() : "N/A");

            if (ingredientsNode.isArray()) {
                for (JsonNode ingredientNode : ingredientsNode) {
                    IngredientDto ingredient = parseIngredient(ingredientNode.asText());
                    log.debug("Parsed ingredient - quantity: '{}', name: '{}'",
                        ingredient.getQuantity(), ingredient.getName());
                    ingredients.add(ingredient);
                }
            }
        } else {
            log.warn("No recipeIngredient field found in schema.org data");
        }

        recipe.setIngredients(ingredients);
        log.info("Total ingredients extracted: {}", ingredients.size());

        // Extract steps/instructions
        List<StepDto> steps = new ArrayList<>();
        if (recipeNode.has("recipeInstructions")) {
            JsonNode instructionsNode = recipeNode.get("recipeInstructions");
            log.debug("Found recipeInstructions node, type: {}", instructionsNode.getNodeType());

            // recipeInstructions can be: string, array of strings, or array of HowToStep objects
            if (instructionsNode.isArray()) {
                int stepNumber = 1;
                for (JsonNode instructionNode : instructionsNode) {
                    String instructionText = null;

                    if (instructionNode.isTextual()) {
                        // Simple text array
                        instructionText = instructionNode.asText();
                    } else if (instructionNode.has("@type") && instructionNode.get("@type").asText().equals("HowToStep")) {
                        // HowToStep object
                        if (instructionNode.has("text")) {
                            instructionText = instructionNode.get("text").asText();
                        }
                    }

                    if (instructionText != null && !instructionText.trim().isEmpty()) {
                        StepDto step = new StepDto();
                        step.setStepNumber(stepNumber++);
                        step.setInstruction(instructionText);
                        steps.add(step);
                    }
                }
            } else if (instructionsNode.isTextual()) {
                // Single text instruction
                StepDto step = new StepDto();
                step.setStepNumber(1);
                step.setInstruction(instructionsNode.asText());
                steps.add(step);
            }
        }
        recipe.setSteps(steps);
        log.info("Total steps extracted: {}", steps.size());

        // Extract prepTime (ISO 8601 format like "PT15M")
        if (recipeNode.has("prepTime")) {
            String prepTime = recipeNode.get("prepTime").asText();
            recipe.setPrepTime(parseIsoDuration(prepTime));
            log.debug("Extracted prepTime: {} -> {}", prepTime, recipe.getPrepTime());
        }

        // Extract cookTime
        if (recipeNode.has("cookTime")) {
            String cookTime = recipeNode.get("cookTime").asText();
            recipe.setCookTime(parseIsoDuration(cookTime));
            log.debug("Extracted cookTime: {} -> {}", cookTime, recipe.getCookTime());
        }

        // Extract servings (recipeYield)
        if (recipeNode.has("recipeYield")) {
            JsonNode yieldNode = recipeNode.get("recipeYield");
            if (yieldNode.isArray() && yieldNode.size() > 0) {
                recipe.setServings(yieldNode.get(0).asText());
            } else {
                recipe.setServings(yieldNode.asText());
            }
            log.debug("Extracted servings: {}", recipe.getServings());
        }

        // Extract category (recipeCategory)
        if (recipeNode.has("recipeCategory")) {
            JsonNode categoryNode = recipeNode.get("recipeCategory");
            if (categoryNode.isArray() && categoryNode.size() > 0) {
                // Join multiple categories with comma
                StringBuilder categories = new StringBuilder();
                for (int i = 0; i < categoryNode.size(); i++) {
                    if (i > 0) categories.append(", ");
                    categories.append(categoryNode.get(i).asText());
                }
                recipe.setCategory(categories.toString());
            } else {
                recipe.setCategory(categoryNode.asText());
            }
            log.debug("Extracted category: {}", recipe.getCategory());
        }

        return recipe;
    }

    /**
     * Parse ingredient text into quantity and name
     * Examples:
     * - "3 cups cooked quinoa" -> quantity="3 cups", name="cooked quinoa"
     * - "(14-ounce) can kidney beans, drained and rinsed" -> quantity="14 ounce", name="kidney beans"
     */
    private IngredientDto parseIngredient(String ingredientText) {
        IngredientDto ingredient = new IngredientDto();
        String text = ingredientText.trim();

        String quantity = "";
        String name = text;

        // Check for parenthetical quantity first: (14-ounce), (15-oz), etc.
        Pattern parenPattern = Pattern.compile("^\\((\\d+[\\s-]*(?:ounce|oz|pound|lb|gram|g|kg))\\)", Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher parenMatcher = parenPattern.matcher(text);

        if (parenMatcher.find()) {
            quantity = parenMatcher.group(1).replace("-", " ").trim();
            text = text.substring(parenMatcher.end()).trim();

            // Remove "can" or "cans" if it's the first word after the quantity
            text = text.replaceFirst("^cans?\\s+", "");
        } else {
            // Pattern to match standard quantity formats:
            // - Numbers (1, 2, 3.5, 1/2, 1 1/2, 1½)
            // - Optional units (cup, cups, tablespoon, tsp, oz, etc.)
            Pattern quantityPattern = Pattern.compile(
                "^([\\d\\s\\/\\.½¼¾⅓⅔⅛⅜⅝⅞]+\\s*(?:cup|cups|tablespoon|tablespoons|tbsp|teaspoon|teaspoons|tsp|" +
                "ounce|ounces|oz|pound|pounds|lb|lbs|gram|grams|g|kilogram|kg|" +
                "milliliter|ml|liter|l|pinch|dash|clove|cloves)?s?)\\s+(.+)$",
                Pattern.CASE_INSENSITIVE
            );

            java.util.regex.Matcher matcher = quantityPattern.matcher(text);

            if (matcher.find()) {
                quantity = matcher.group(1).trim();
                text = matcher.group(2).trim();
            }
        }

        // Remove preparation instructions (text after comma)
        int commaIndex = text.indexOf(',');
        if (commaIndex > 0) {
            name = text.substring(0, commaIndex).trim();
        } else {
            name = text.trim();
        }

        // Remove parenthetical notes from name like "(from 1 cup uncooked)"
        name = name.replaceAll("\\s*\\([^)]*\\)\\s*", " ").trim();

        ingredient.setQuantity(quantity);
        ingredient.setName(name);

        return ingredient;
    }

    /**
     * Parse ISO 8601 duration format to human-readable text
     * Examples: PT15M -> "15 minutes", PT1H30M -> "1 hour 30 minutes", PT250M -> "4 hours 10 minutes"
     */
    private String parseIsoDuration(String isoDuration) {
        if (isoDuration == null || isoDuration.isEmpty() || !isoDuration.startsWith("PT") && !isoDuration.startsWith("P")) {
            return isoDuration; // Return as-is if not ISO format
        }

        try {
            // Remove 'P' prefix and split by 'T' to separate date and time parts
            String duration = isoDuration.substring(1); // Remove 'P'

            int days = 0, hours = 0, minutes = 0;

            // Check for days (before T)
            if (duration.contains("D")) {
                int dIndex = duration.indexOf("D");
                days = Integer.parseInt(duration.substring(0, dIndex));
                duration = duration.substring(dIndex + 1);
            }

            // Remove 'T' if present
            if (duration.startsWith("T")) {
                duration = duration.substring(1);
            }

            // Extract hours
            if (duration.contains("H")) {
                int hIndex = duration.indexOf("H");
                hours = Integer.parseInt(duration.substring(0, hIndex));
                duration = duration.substring(hIndex + 1);
            }

            // Extract minutes
            if (duration.contains("M")) {
                int mIndex = duration.indexOf("M");
                minutes = Integer.parseInt(duration.substring(0, mIndex));
            }

            // Convert large minute values to hours
            if (minutes >= 60 && hours == 0) {
                hours = minutes / 60;
                minutes = minutes % 60;
            }

            // Build readable string
            StringBuilder result = new StringBuilder();
            if (days > 0) {
                result.append(days).append(days == 1 ? " day" : " days");
            }
            if (hours > 0) {
                if (result.length() > 0) result.append(" ");
                result.append(hours).append(hours == 1 ? " hour" : " hours");
            }
            if (minutes > 0) {
                if (result.length() > 0) result.append(" ");
                result.append(minutes).append(minutes == 1 ? " minute" : " minutes");
            }

            return result.length() > 0 ? result.toString() : isoDuration;
        } catch (Exception e) {
            log.warn("Failed to parse ISO duration: {}", isoDuration);
            return isoDuration; // Return original if parsing fails
        }
    }

    private RecipeDto extractUsingLLM(String htmlContent, String url) throws IOException, InterruptedException {
        if (openaiApiKey == null || openaiApiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured. Set openai.api.key in application.properties");
        }

        log.debug("Calling OpenAI API for recipe extraction...");

        // Strip HTML tags for cleaner input
        String cleanText = htmlContent.replaceAll("<script[^>]*>.*?</script>", "")
                .replaceAll("<style[^>]*>.*?</style>", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Limit text size to avoid token limits
        if (cleanText.length() > 8000) {
            cleanText = cleanText.substring(0, 8000);
        }

        String prompt = String.format(
                "Extract the recipe information from the following webpage content and return it as JSON with this exact structure:\n" +
                "{\n" +
                "  \"name\": \"recipe name\",\n" +
                "  \"description\": \"brief description\",\n" +
                "  \"prepTime\": \"preparation time (e.g., '15 minutes' or 'PT15M')\",\n" +
                "  \"cookTime\": \"cooking time (e.g., '30 minutes' or 'PT30M')\",\n" +
                "  \"servings\": \"number of servings (e.g., '4 servings' or '4')\",\n" +
                "  \"category\": \"recipe category (e.g., 'Appetizer', 'Main Course', 'Dessert', 'Salad', etc.)\",\n" +
                "  \"ingredients\": [{\"name\": \"ingredient name\", \"quantity\": \"amount\"}],\n" +
                "  \"steps\": [{\"stepNumber\": 1, \"instruction\": \"step instruction\"}]\n" +
                "}\n\n" +
                "Webpage content:\n%s", cleanText
        );

        String requestBody = String.format(
                "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": %s}], \"temperature\": 0.3}",
                objectMapper.writeValueAsString(prompt)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("OpenAI API request failed. Status: " + response.statusCode() + ", Body: " + response.body());
        }

        JsonNode responseNode = objectMapper.readTree(response.body());
        String recipeJson = responseNode
                .get("choices")
                .get(0)
                .get("message")
                .get("content")
                .asText();

        // Parse the JSON response
        RecipeDto recipe = objectMapper.readValue(recipeJson, RecipeDto.class);
        recipe.setSourceType(SourceType.URL);
        recipe.setSourceValue(url);

        return recipe;
    }
}
