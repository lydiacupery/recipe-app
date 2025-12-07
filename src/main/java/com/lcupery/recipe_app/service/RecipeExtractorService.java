package com.lcupery.recipe_app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
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

    @Value("${jina.enabled:true}")
    private boolean jinaEnabled;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public RecipeExtractorService() {
        // Configure ObjectMapper to handle non-standard JSON (with comments)
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    /**
     * Extract recipe from URL using smart fallback approach:
     * 1. Try direct HTTP + schema.org (fast, works for most sites)
     * 2. Try HtmlUnit without JS + schema.org (handles redirects, avoids JS errors)
     * 3. Try HtmlUnit with JS + schema.org (for JS-heavy sites, may have JS errors)
     * 4. Try Jina AI + schema.org (last resort for blocked sites)
     * 5. Fall back to LLM if all schema.org attempts fail
     */
    public RecipeDto extractRecipeFromUrl(String url) throws IOException, InterruptedException {
        log.info("Starting recipe extraction from URL: {}", url);

        String htmlContent = null;
        RecipeDto recipe = null;

        // Method 1: Try direct HTTP first (fastest, works for most recipe sites)
        try {
            log.info("Method #1: Direct HTTP fetch + schema.org extraction");
            htmlContent = fetchWithDirectHttp(url);
            recipe = extractFromSchemaOrg(htmlContent, url);
            if (recipe != null) {
                log.info("✓ Successfully extracted recipe using direct HTTP + schema.org");
                return recipe;
            }
            log.info("Direct HTTP succeeded but no schema.org data found, trying HtmlUnit...");
        } catch (Exception e) {
            log.warn("✗ Direct HTTP failed: {}", e.getMessage());
        }

        // Method 2: Try HtmlUnit without JavaScript first (fast, avoids JS errors)
        try {
            log.info("Method #2: HtmlUnit (no JS) fetch + schema.org extraction");
            htmlContent = fetchWithHtmlUnitNoJs(url);
            recipe = extractFromSchemaOrg(htmlContent, url);
            if (recipe != null) {
                log.info("✓ Successfully extracted recipe using HtmlUnit (no JS) + schema.org");
                return recipe;
            }
            log.info("HtmlUnit (no JS) succeeded but no schema.org data found");
        } catch (Exception e) {
            log.warn("✗ HtmlUnit (no JS) failed: {}", e.getMessage());
        }

        // Method 3: Try HtmlUnit WITH JavaScript (for JS-heavy sites, may have errors)
        try {
            log.info("Method #3: HtmlUnit (with JS) fetch + schema.org extraction");
            htmlContent = fetchWithHtmlUnit(url);
            recipe = extractFromSchemaOrg(htmlContent, url);
            if (recipe != null) {
                log.info("✓ Successfully extracted recipe using HtmlUnit (with JS) + schema.org");
                return recipe;
            }
            log.info("HtmlUnit (with JS) succeeded but no schema.org data found, trying Jina AI...");
        } catch (Exception e) {
            log.warn("✗ HtmlUnit (with JS) failed: {}", e.getMessage());
        }

        // Method 4: Try Jina AI as last resort (if enabled)
        if (jinaEnabled) {
            try {
                log.info("Method #4: Jina AI fetch + schema.org extraction");
                htmlContent = fetchWithJinaAi(url);
                recipe = extractFromSchemaOrg(htmlContent, url);
                if (recipe != null) {
                    log.info("✓ Successfully extracted recipe using Jina AI + schema.org");
                    return recipe;
                }
                log.info("Jina AI succeeded but no schema.org data found, falling back to LLM...");
            } catch (Exception e) {
                log.warn("✗ Jina AI failed: {}", e.getMessage());
            }
        }

        // Method 5: Fall back to LLM extraction if we have any HTML content
        if (htmlContent == null) {
            throw new IOException("All fetch methods failed - unable to retrieve webpage content");
        }

        log.info("Method #5: LLM extraction (no schema.org data found in any method)");
        recipe = extractUsingLLM(htmlContent, url);
        log.info("✓ Successfully extracted recipe using LLM approach");
        return recipe;
    }

    /**
     * Check if Jina AI returned an error response instead of actual content
     * Error responses are JSON objects with fields like "code", "name", "status", "message"
     */
    private boolean isJinaErrorResponse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return true;
        }

        // Check if response looks like a JSON error (starts with { and contains "code" or "error")
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                // Jina error responses have these fields
                if (node.has("code") && node.has("status") && node.has("message")) {
                    String message = node.get("message").asText();
                    log.warn("Jina AI error detected: {}", message);
                    return true;
                }
                // Generic error response
                if (node.has("error")) {
                    log.warn("Jina AI error detected: {}", node.get("error").asText());
                    return true;
                }
            } catch (Exception e) {
                // Not valid JSON, probably actual content
                return false;
            }
        }

        // If content is suspiciously short (less than 100 chars), it might be an error
        if (content.length() < 100) {
            log.warn("Jina AI returned suspiciously short content ({}), treating as error", content.length());
            return true;
        }

        return false;
    }

    /**
     * Fetch method 1: Direct HTTP (fast, works for most sites)
     */
    private String fetchWithDirectHttp(String url) throws IOException, InterruptedException {
        log.debug("Fetching with direct HTTP...");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Direct HTTP failed with status: " + response.statusCode());
        }
        log.debug("✓ Direct HTTP successful, content length: {}", response.body().length());
        return response.body();
    }

    /**
     * Fetch method 2a: HtmlUnit without JavaScript (avoids JS errors)
     */
    private String fetchWithHtmlUnitNoJs(String url) throws IOException {
        log.debug("Fetching with HtmlUnit (no JavaScript)...");
        try (WebClient webClient = new WebClient(BrowserVersion.CHROME)) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(false); // Disable JS to avoid errors
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setTimeout(15000);
            webClient.getOptions().setPrintContentOnFailingStatusCode(false);

            HtmlPage page = webClient.getPage(url);
            String html = page.asXml();
            log.debug("✓ HtmlUnit (no JS) successful, content length: {}", html.length());
            return html;
        }
    }

    /**
     * Fetch method 2b: HtmlUnit WITH JavaScript (for dynamic sites, may have errors)
     */
    private String fetchWithHtmlUnit(String url) throws IOException {
        log.debug("Fetching with HtmlUnit (renders JavaScript)...");
        try (WebClient webClient = new WebClient(BrowserVersion.CHROME)) {
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setTimeout(15000);

            // Suppress all JS warnings and errors - we only need the HTML
            webClient.getOptions().setPrintContentOnFailingStatusCode(false);
            java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.SEVERE);

            HtmlPage page = webClient.getPage(url);

            // Try to wait for JS, but don't fail if it errors
            try {
                webClient.waitForBackgroundJavaScript(5000);
            } catch (Exception e) {
                log.debug("Background JavaScript had errors (ignoring): {}", e.getMessage());
            }

            String html = page.asXml();
            log.debug("✓ HtmlUnit successful, content length: {}", html.length());
            return html;
        } catch (com.gargoylesoftware.htmlunit.ScriptException e) {
            log.warn("HtmlUnit JavaScript error (rethrowing to try next method): {}", e.getMessage());
            throw new IOException("HtmlUnit JavaScript compilation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch method 3: Jina AI (last resort, often blocked)
     */
    private String fetchWithJinaAi(String url) throws IOException, InterruptedException {
        log.debug("Fetching with Jina AI Reader...");
        String jinaUrl = "https://r.jina.ai/" + url;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jinaUrl))
                .header("User-Agent", "Mozilla/5.0 (Recipe App)")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Jina AI failed with status: " + response.statusCode());
        }

        String content = response.body();
        if (isJinaErrorResponse(content)) {
            throw new IOException("Jina AI blocked (SecurityCompromiseError or rate limit)");
        }

        log.debug("✓ Jina AI successful, content length: {}", content.length());
        return content;
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

            // Skip if content is empty or too short
            if (jsonContent.length() < 10) {
                log.debug("Skipping JSON-LD block #{} - content too short", scriptCount);
                continue;
            }

            try {
                // Try to parse as JSON first - if it parses, it's valid JSON-LD regardless of content
                JsonNode rootNode = objectMapper.readTree(jsonContent);
                log.debug("Successfully parsed JSON-LD block #{} as valid JSON", scriptCount);

                // Handle both single objects and arrays
                JsonNode recipeNode = findRecipeNode(rootNode);
                if (recipeNode != null) {
                    log.debug("Found Recipe node in JSON-LD block #{}", scriptCount);
                    RecipeDto recipe = parseSchemaOrgRecipe(recipeNode, url);
                    log.info("✓ Successfully extracted recipe '{}' from schema.org", recipe.getName());
                    return recipe;
                } else {
                    log.debug("No Recipe node found in JSON-LD block #{} (has @type: {})",
                        scriptCount,
                        rootNode.has("@type") ? rootNode.get("@type").asText() : "none");
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
                int index = 0;
                for (JsonNode ingredientNode : ingredientsNode) {
                    String rawIngredient = ingredientNode.asText();
                    log.debug("Raw ingredient #{}: '{}'", index, rawIngredient);
                    IngredientDto ingredient = parseIngredient(rawIngredient);
                    log.debug("Parsed ingredient #{} - quantity: '{}', name: '{}'",
                        index, ingredient.getQuantity(), ingredient.getName());
                    ingredients.add(ingredient);
                    index++;
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

        // Extract image URL
        if (recipeNode.has("image")) {
            JsonNode imageNode = recipeNode.get("image");
            String imageUrl = extractImageUrl(imageNode);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                recipe.setImageUrl(imageUrl);
                log.debug("Extracted image URL: {}", imageUrl);
            }
        }

        return recipe;
    }

    /**
     * Extract image URL from schema.org image field
     * The image field can be:
     * - A string URL
     * - An object with "url" property (ImageObject)
     * - An array of strings or objects
     */
    private String extractImageUrl(JsonNode imageNode) {
        if (imageNode == null) {
            return null;
        }

        // Case 1: Simple string URL
        if (imageNode.isTextual()) {
            return imageNode.asText();
        }

        // Case 2: Array of images - take the first one
        if (imageNode.isArray() && imageNode.size() > 0) {
            JsonNode firstImage = imageNode.get(0);
            return extractImageUrl(firstImage); // Recursive call for first element
        }

        // Case 3: ImageObject with url property
        if (imageNode.isObject() && imageNode.has("url")) {
            JsonNode urlNode = imageNode.get("url");
            if (urlNode.isTextual()) {
                return urlNode.asText();
            }
        }

        // Case 4: ImageObject with contentUrl property (alternative field)
        if (imageNode.isObject() && imageNode.has("contentUrl")) {
            JsonNode contentUrlNode = imageNode.get("contentUrl");
            if (contentUrlNode.isTextual()) {
                return contentUrlNode.asText();
            }
        }

        return null;
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
        log.debug("Parsing ingredient: '{}'", ingredientText);

        String quantity = null;
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
            // Try to extract quantity from the beginning of the string
            // Pattern matches: number + optional fraction + optional unit + space + rest
            Pattern quantityPattern = Pattern.compile(
                "^(\\d+(?:\\s*[\\d\\/½¼¾⅓⅔⅛⅜⅝⅞]*)?)" +  // Number with optional fractions
                "\\s*" +                                      // Optional whitespace
                "((?:cup|tablespoon|tbsp|teaspoon|tsp|" +     // Optional unit (singular)
                "ounce|oz|pound|lb|gram|g|kilogram|kg|" +
                "milliliter|ml|liter|litre|pint|quart|gallon|" +
                "pinch|dash|clove|can|package|pkg)s?)??" +   // Optional 's' for plural, non-greedy
                "\\s+" +                                      // Required space before ingredient name
                "(.+)$",                                      // Rest of the text (ingredient name)
                Pattern.CASE_INSENSITIVE
            );

            java.util.regex.Matcher matcher = quantityPattern.matcher(text);

            if (matcher.find()) {
                String numberPart = matcher.group(1).trim();
                String unitPart = matcher.group(2);
                String namePart = matcher.group(3).trim();

                // Combine number and unit for quantity
                if (unitPart != null && !unitPart.isEmpty()) {
                    quantity = numberPart + " " + unitPart;
                } else {
                    quantity = numberPart;
                }
                text = namePart;
                log.debug("  → Matched quantity pattern: number='{}', unit='{}', quantity='{}', remaining='{}'",
                    numberPart, unitPart, quantity, text);
            } else {
                log.debug("  → No quantity pattern matched, treating entire text as name");
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

        // Set quantity to null if empty rather than empty string
        if (quantity != null && quantity.isEmpty()) {
            quantity = null;
        }

        ingredient.setQuantity(quantity);
        ingredient.setName(name);

        log.debug("  → Final result: quantity='{}', name='{}'", quantity, name);
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

        // Extract image URLs before stripping HTML
        List<String> imageUrls = extractImageUrlsFromHtml(htmlContent);
        String primaryImageUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

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
                "  \"imageUrl\": \"URL of the recipe image if present (look for <img> tags with src attributes)\",\n" +
                "  \"ingredients\": [{\"name\": \"ingredient name\", \"quantity\": \"amount\"}],\n" +
                "  \"steps\": [{\"stepNumber\": 1, \"instruction\": \"step instruction\"}]\n" +
                "}\n\n" +
                "IMPORTANT RULES:\n" +
                "1. ONLY extract information that is explicitly present in the source content\n" +
                "2. DO NOT infer, assume, or add any steps that are not written in the source\n" +
                "3. DO NOT add common cooking steps unless they are explicitly stated\n" +
                "4. If steps/instructions are not present in the source, return an empty steps array\n" +
                "5. Copy the exact wording from the source - do not paraphrase or rewrite\n" +
                "6. For imageUrl, extract the FIRST high-quality recipe image URL found in the page\n" +
                "7. If a field is not present, use null or empty string/array\n\n" +
                "Webpage content:\n%s", cleanText
        );

        String requestBody = String.format(
                "{\"model\": \"gpt-3.5-turbo\", \"messages\": [{\"role\": \"user\", \"content\": %s}], \"temperature\": 0.1}",
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

        // If LLM didn't extract an image but we found one in HTML, use that
        if ((recipe.getImageUrl() == null || recipe.getImageUrl().isEmpty()) && primaryImageUrl != null) {
            recipe.setImageUrl(primaryImageUrl);
            log.debug("Using HTML-extracted image URL: {}", primaryImageUrl);
        }

        return recipe;
    }

    /**
     * Extract image URLs from HTML content
     * Looks for <img> tags with src attributes
     */
    private List<String> extractImageUrlsFromHtml(String htmlContent) {
        List<String> imageUrls = new ArrayList<>();

        // Pattern to match img tags with src attributes
        Pattern imgPattern = Pattern.compile(
            "<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = imgPattern.matcher(htmlContent);

        while (matcher.find()) {
            String imgUrl = matcher.group(1);

            // Filter out common non-recipe images (icons, logos, ads, etc.)
            if (!imgUrl.contains("icon") &&
                !imgUrl.contains("logo") &&
                !imgUrl.contains("avatar") &&
                !imgUrl.contains("ad") &&
                !imgUrl.contains("pixel") &&
                !imgUrl.contains("tracking") &&
                !imgUrl.endsWith(".gif") &&
                !imgUrl.contains("1x1") &&
                imgUrl.length() > 20) { // Skip very short URLs (likely tracking pixels)

                imageUrls.add(imgUrl);
            }
        }

        log.debug("Found {} potential recipe images in HTML", imageUrls.size());
        return imageUrls;
    }
}
