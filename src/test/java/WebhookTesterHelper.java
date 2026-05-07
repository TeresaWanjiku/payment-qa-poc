import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class WebhookTesterHelper {

    private static final String EVENTS_FILE =
            "C:/Projects/payment-qa-poc/webhook-listener/events.json";

    private static final int TIMEOUT_SECONDS = 30;
    private static final int POLL_INTERVAL_MS = 1000;

    /**
     * Polls events.json until a charge.success event appears for the given reference.
     * Returns the matching event node, or throws if timed out.
     */
    public static JsonNode waitForChargeSuccess(String reference) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        long deadline = System.currentTimeMillis() + (TIMEOUT_SECONDS * 1000L);

        System.out.println("[WebhookHelper] Waiting for charge.success | ref: " + reference);

        while (System.currentTimeMillis() < deadline) {
            File file = new File(EVENTS_FILE);

            if (file.exists()) {
                JsonNode events = mapper.readTree(file);

                if (events.isArray()) {
                    for (JsonNode event : events) {
                        String eventType = event.path("event").asText();
                        String eventRef  = event.path("reference").asText();

                        if ("charge.success".equals(eventType) && reference.equals(eventRef)) {
                            System.out.println("[WebhookHelper] Event found for ref: " + reference);
                            return event;
                        }
                    }
                }
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        throw new AssertionError(
                "[WebhookHelper] Timed out after " + TIMEOUT_SECONDS +
                        "s waiting for charge.success with ref: " + reference
        );
    }

    /**
     * Deletes events.json so each test run starts clean.
     * Call this in @BeforeClass or @BeforeMethod.
     */
    public static void clearEvents() {
        File file = new File(EVENTS_FILE);
        if (file.exists()) {
            file.delete();
            System.out.println("[WebhookHelper] Cleared events.json");
        }
    }
}