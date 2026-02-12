package io.github.demiourgoi.linoleum.examples;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


public class MistralClientTest {

    public static final String MISTRALAI_API_KEY = System.getenv("MISTRAL_API_KEY");

    @Before
    public void checkApiKeyAvailability() {
        try {
            MistralClient.checkMistralApiKeyAvailableOnEnv();
        } catch (MistralClientException mce) {
            Assume.assumeNoException(mce);
        }
    }

    @Test
    public void testMistralClientSendCompletion() {
        // Create the client using try-with-resources since it's now AutoCloseable
        try (MistralClient client = new MistralClient(MISTRALAI_API_KEY)) {
            // Send a test message
            String testMessage = "Hello! How are you doing today?";
            MistralChatResponse response = client.sendCompletion(testMessage);

            // Verify the response is not null
            assertNotNull("Response should not be null", response);

            // Verify we got some choices back
            assertNotNull("Choices should not be null", response.getChoices());
            assertFalse("Choices should not be empty", response.getChoices().isEmpty());

            // Verify we can extract choice contents
            assertNotNull("Choice contents should not be null", response.getChoiceContents());
            assertFalse("Choice contents should not be empty", response.getChoiceContents().isEmpty());

            System.out.println("Received response with choice contents: " + String.join(", ", response.getChoiceContents()));
        }
    }
}