package io.github.demiourgoi.linoleum.examples;

import com.google.gson.Gson;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;

import java.util.Arrays;

/**
 * Client for interacting with the Mistral AI API
 */
public class MistralClient implements AutoCloseable {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Gson gson;
    private final CloseableHttpClient httpClient;

    public static final String MISTRALAI_API_KEY_ENV_VAR = "MISTRAL_API_KEY";

    public static String getMistralApiKey() {
        return System.getenv(MISTRALAI_API_KEY_ENV_VAR);
    }

    /**
     * Throws a MistralClientException is the Mistral API key is not available on
     * the environment
     */
    public static void checkMistralApiKeyAvailableOnEnv() {
        String mistralApiKey = getMistralApiKey();
        if (mistralApiKey == null || mistralApiKey.trim().isEmpty()) {
            throw new MistralClientException(MISTRALAI_API_KEY_ENV_VAR +
                    " environment variable is not set or is empty. Skipping test.");
        }
    }

    /**
     * Closes the HTTP client and releases associated resources
     * 
     * @throws MistralClientException if there is an error closing the client
     */
    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            throw new MistralClientException("Failed to close HTTP client", e);
        }
    }

    public MistralClient() {
        this(getMistralApiKey());
    }

    /**
     * Constructor for MistralClient
     * 
     * @param apiKey The Mistral API key for authentication
     */
    public MistralClient(String apiKey) {
        this(apiKey, "https://api.mistral.ai/v1", "mistral-small-latest");
    }

    /**
     * Constructor for MistralClient with customizable parameters
     * 
     * @param apiKey  The Mistral API key for authentication
     * @param baseUrl The base URL for the Mistral API
     * @param model   The model to use for completions
     */
    public MistralClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.gson = new Gson();
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Sends a completion request to the Mistral API
     * 
     * @param messageContent The content of the message to send
     * @return MistralChatResponse containing the API response
     * @throws MistralClientException If there is an error communicating with the
     *                                API or the response is not successful
     */
    public MistralChatResponse sendCompletion(String messageContent) {
        // Create the chat request using the structured classes
        MistralChatRequest.Message message = new MistralChatRequest.Message("user", messageContent);
        MistralChatRequest chatRequest = new MistralChatRequest(Arrays.asList(message), model);

        // Serialize to JSON using Gson
        String jsonRequest = this.gson.toJson(chatRequest);

        // Build the HTTP request
        ClassicHttpRequest postChat = ClassicRequestBuilder.post(baseUrl + "/chat/completions")
                .addHeader("Authorization", String.format("Bearer %s", apiKey))
                .addHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(jsonRequest))
                .build();

        // Execute the request and get the response
        try (CloseableHttpResponse response = httpClient.execute(postChat)) {
            // Check response status code
            int statusCode = response.getCode();
            if (statusCode != 200) {
                throw new MistralClientException(statusCode, response.getReasonPhrase());
            }

            // Get the response entity and parse it
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String responseBody = EntityUtils.toString(entity);
                // Parse the response using Gson
                return this.gson.fromJson(responseBody, MistralChatResponse.class);
            } else {
                throw new MistralClientException("Empty response from Mistral API");
            }
        } catch (IOException e) {
            throw new MistralClientException("Failed to communicate with Mistral API", e);
        } catch (ParseException e) {
            throw new MistralClientException("Failed to parse response from Mistral API", e);
        }
    }
}