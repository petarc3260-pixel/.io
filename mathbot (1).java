// MathBotApplication.java
// A complete Spring Boot application for math chat with local evaluation and OpenAI fallback.
// You need Java 17+, Maven/Gradle, and an OpenAI API key.
// Dependencies: spring-boot-starter-web, exp4j, okhttp, jackson-databind

package com.example.mathbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import okhttp3.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;

@SpringBootApplication
@RestController
public class MathBotApplication {

    // TODO: Set your OpenAI API key here
    private static final String OPENAI_API_KEY = "YOUR_OPENAI_API_KEY";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        SpringApplication.run(MathBotApplication.class, args);
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> payload) {
        String query = (String) payload.getOrDefault("query", "");
        List<Map<String, String>> history = getHistory(payload);

        String answer = trySolveMath(query);
        if (answer == null) {
            answer = askGptMath(query, history);
        }

        Map<String, String> response = new HashMap<>();
        response.put("answer", answer);
        return ResponseEntity.ok(response);
    }

    // Try to solve with exp4j (arithmetic only)
    private String trySolveMath(String query) {
        try {
            Expression expr = new ExpressionBuilder(query).build();
            double result = expr.evaluate();
            return "The answer is: " + result;
        } catch (Exception e) {
            return null;
        }
    }

    // If math fails, ask OpenAI
    private String askGptMath(String query, List<Map<String, String>> history) {
        try {
            OkHttpClient client = new OkHttpClient();

            List<Map<String, String>> messages = new ArrayList<>(history);
            messages.add(Map.of("role", "user", "content", query));

            // Build request payload
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("model", "gpt-3.5-turbo");
            reqBody.put("temperature", 0.2);
            reqBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(reqBody),
                MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .post(body)
                .build();

            Response response = client.newCall(request).execute();
            String resBody = response.body().string();

            // Parse response JSON to extract the answer
            Map<String, Object> map = objectMapper.readValue(resBody, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            return "Unable to process your request at the moment.";
        }
    }

    // Helper to safely extract history as List<Map<String, String>>
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> getHistory(Map<String, Object> payload) {
        Object obj = payload.get("history");
        if (obj instanceof List<?>) {
            List<?> list = (List<?>) obj;
            List<Map<String, String>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    Map<?, ?> map = (Map<?, ?>) item;
                    Map<String, String> strMap = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                            strMap.put((String) entry.getKey(), (String) entry.getValue());
                        }
                    }
                    out.add(strMap);
                }
            }
            return out;
        }
        return new ArrayList<>();
    }
}
