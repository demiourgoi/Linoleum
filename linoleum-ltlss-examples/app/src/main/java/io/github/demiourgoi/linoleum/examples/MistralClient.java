package io.github.demiourgoi.linoleum.examples;

import java.io.IOException;
import java.util.Arrays;

import com.google.gson.Gson;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.ParseException;

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

    static {
        // Hack to avoid java.lang.illegalargumentexception: error in security property.
        // constraint unknown: c2tnb191v1 when calling httpclients.createdefault()
        // in Java 8. We should upgrade to Java 11 ASAP
        // This force-overwrites the property in memory before the
        // SSLContext class can read the corrupt java.security file.
        java.security.Security.setProperty("jdk.disabled.namedCurves", "");
    }

    public static String getMistralApiKey() {
        return System.getenv(MISTRALAI_API_KEY_ENV_VAR);
    }

    /**
     * Throws a MistralClientException is the Mistral API key is not available on
     * the environment
     */
    public static void checkMistralApiKeyAvailableOnEnv() {
        if (!isMistralApiKeyAvailableOnEnv()) {
            throw new MistralClientException(MISTRALAI_API_KEY_ENV_VAR +
                    " environment variable is not set or is empty. Skipping test.");
        }
    }

    public static boolean isMistralApiKeyAvailableOnEnv() {
        String mistralApiKey = getMistralApiKey();
        return !(mistralApiKey == null || mistralApiKey.trim().isEmpty());
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
     * Response handler for Mistral API responses
     */
    private class MistralResponseHandler implements HttpClientResponseHandler<MistralChatResponse> {
        @Override
        public MistralChatResponse handleResponse(
                final ClassicHttpResponse response) throws IOException {
            // Check response status code
            int statusCode = response.getCode();
            if (statusCode != 200) {
                response.close();
                throw new MistralClientException(statusCode, response.getReasonPhrase());
            }

            // Get the response entity and parse it
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    String responseBody = EntityUtils.toString(entity);
                    // Parse the response using Gson
                    return gson.fromJson(responseBody, MistralChatResponse.class);
                } catch (ParseException e) {
                    throw new MistralClientException("Failed to parse response from Mistral API", e);
                } finally {
                    response.close();
                }
            } else {
                response.close();
                throw new MistralClientException("Empty response from Mistral API");
            }
        }
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
        String jsonRequest = gson.toJson(chatRequest);

        // Build the HTTP request
        ClassicHttpRequest postChat = ClassicRequestBuilder.post(baseUrl + "/chat/completions")
                .addHeader("Authorization", String.format("Bearer %s", apiKey))
                .addHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(jsonRequest))
                .build();

        // FIXME add retries
        // Execute the request using the recommended approach with response handler
        try {
            return httpClient.execute(postChat, new MistralResponseHandler());
        } catch (IOException e) {
            throw new MistralClientException("Failed to communicate with Mistral API", e);
        }
    }
}