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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.hc.core5.http.ParseException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HelloMistralTest {

    public static final String MISTRALAI_API_KEY = System.getenv("MISTRAL_API_KEY");

    @Before
    public void checkApiKeyAvailability() {
        Assume.assumeTrue("MISTRALAI_API_KEY environment variable is not set or is empty. Skipping test.",
                          MISTRALAI_API_KEY != null && !MISTRALAI_API_KEY.trim().isEmpty());
    }

    @Test
    public void testSimplePost() throws IOException, ParseException {

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            // Create the chat request using the structured classes
            MistralChatRequest.Message message = new MistralChatRequest.Message("user", "Hello! How are you doing today?");
            MistralChatRequest chatRequest = new MistralChatRequest(Arrays.asList(message), "mistral-small-latest");

            // Serialize to JSON using Gson
            Gson gson = new Gson();
            String jsonRequest = gson.toJson(chatRequest);

            System.out.println("Sending request: " + jsonRequest);

            ClassicHttpRequest postChat = ClassicRequestBuilder.post("https://api.mistral.ai/v1/chat/completions")
                .addHeader("Authorization", String.format("Bearer %s", MISTRALAI_API_KEY))
                .addHeader("Content-Type", "application/json")
                .setEntity(new StringEntity(jsonRequest))
                .build();

            // Execute the request and get the response
            // FIXME deprecated
            // FIXME retries
            try (CloseableHttpResponse response = httpclient.execute(postChat)) {
                System.out.println("Response status: " + response.getCode() + " " + response.getReasonPhrase());
                assertEquals(200, response.getCode());

                // Get the response entity and parse it
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String responseBody = EntityUtils.toString(entity);
                    System.out.println("Full response body: " + responseBody);

                    // Parse the response using Gson
                    MistralChatResponse chatResponse = gson.fromJson(responseBody, MistralChatResponse.class);

                    // Extract and print the content from choices
                    List<String> choiceContents = chatResponse.getChoiceContents();
                    System.out.println("Extracted choice contents:");
                    for (int i = 0; i < choiceContents.size(); i++) {
                        System.out.println("Choice " + i + " content: " + choiceContents.get(i));
                    }
                }
            }
        }

        assertTrue(true);
    }
}
