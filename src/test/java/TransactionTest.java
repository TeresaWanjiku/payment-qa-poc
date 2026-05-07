import io.restassured.http.ContentType;
import org.testng.annotations.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

public class TransactionTest extends BaseTest {

    @Test
    public void TC01_shouldReturn200OnValidPaymentRequest() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body("{\"email\": \"test@example.com\", \"amount\": \"10000\"}")
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data.reference", notNullValue());
    }

    @Test
    public void TC02_shouldReturnValidSchemaOnInitialize() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body("{\"email\": \"test@example.com\", \"amount\": \"10000\"}")
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("message", equalTo("Authorization URL created"))
                .body("data.authorization_url", notNullValue())
                .body("data.access_code", notNullValue())
                .body("data.reference", notNullValue());
    }
    @Test(groups = "webhook")
    public void TC03_shouldReceiveWebhookOnSuccessfulPayment() throws Exception {
        // Clear any leftover events from previous runs
        WebhookTesterHelper.clearEvents();

        // Step 1: Initialize a payment and extract the reference
        String reference =
                given()
                        .header("Authorization", "Bearer " + SECRET_KEY)
                        .contentType(ContentType.JSON)
                        .body("{\"email\": \"test@example.com\", \"amount\": \"10000\"}")
                        .when()
                        .post("/transaction/initialize")
                        .then()
                        .statusCode(200)
                        .body("status", equalTo(true))
                        .extract()
                        .path("data.reference");


        System.out.println("[TC03] Payment initialized | ref: " + reference);

        // Write reference to file so test_webhook.py can pick it up
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("C:/Projects/payment-qa-poc/webhook-listener/current-ref.txt"),
                reference
        );

        // Step 2: Wait for the webhook to arrive (Paystack posts it after sandbox charge)
        com.fasterxml.jackson.databind.JsonNode event =
                WebhookTesterHelper.waitForChargeSuccess(reference);

        // Step 3: Assert the webhook payload is correct
        org.testng.Assert.assertEquals(
                event.path("event").asText(), "charge.success",
                "Expected event type charge.success");

        org.testng.Assert.assertEquals(
                event.path("reference").asText(), reference,
                "Webhook reference should match initialized reference");

        org.testng.Assert.assertEquals(
                event.path("amount").asInt(), 10000,
                "Webhook amount should be 10000 kobo");

        org.testng.Assert.assertEquals(
                event.path("currency").asText(), "KES",
                "Webhook currency should be KES");

        System.out.println("[TC03] Webhook verified successfully for ref: " + reference);
    }
    @Test(groups = "webhook")
    public void TC04_shouldReturnSuccessStatusAfterPaymentCompleted() throws Exception {
        WebhookTesterHelper.clearEvents();

        // Step 1: Initialize a fresh payment
        String reference =
                given()
                        .header("Authorization", "Bearer " + SECRET_KEY)
                        .contentType(ContentType.JSON)
                        .body("{\"email\": \"test@example.com\", \"amount\": \"10000\"}")
                        .when()
                        .post("/transaction/initialize")
                        .then()
                        .statusCode(200)
                        .body("status", equalTo(true))
                        .extract()
                        .path("data.reference");

        System.out.println("[TC04] Payment initialized | ref: " + reference);

        java.nio.file.Files.writeString(
                java.nio.file.Path.of("C:/Projects/payment-qa-poc/webhook-listener/current-ref.txt"),
                reference
        );

        // Step 2: Verify transaction via Paystack API — original assertions preserved
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .when()
                .get("/transaction/verify/" + reference)
                .then()
                .statusCode(200)
                .body("status", equalTo(true))
                .body("data.reference", equalTo(reference));

        // Step 3: Assert webhook arrived with correct payload
        com.fasterxml.jackson.databind.JsonNode event =
                WebhookTesterHelper.waitForChargeSuccess(reference);

        org.testng.Assert.assertEquals(
                event.path("event").asText(), "charge.success",
                "Expected event type charge.success");

        org.testng.Assert.assertEquals(
                event.path("reference").asText(), reference,
                "Webhook reference should match initialized reference");

        org.testng.Assert.assertEquals(
                event.path("amount").asInt(), 10000,
                "Webhook amount should be 10000");

        org.testng.Assert.assertEquals(
                event.path("currency").asText(), "KES",
                "Webhook currency should be KES");

        System.out.println("[TC04] Webhook verified successfully for ref: " + reference);
    }
    @Test
    public void TC05_shouldReturn400OnMissingAmountField() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body("{\"email\": \"test@example.com\"}")
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(400)
                .body("status", equalTo(false))
                .body("message", notNullValue());
    }
    @Test
    public void TC06_shouldReturn400OnMissingEmailField() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body("{\"amount\": \"10000\"}")
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(400)
                .body("status", equalTo(false))
                .body("message", notNullValue());
    }
    @Test
    public void TC07_shouldReturn401OnInvalidApiKey() {
        given()
                .header("Authorization", "Bearer invalid_key_12345")
                .contentType(ContentType.JSON)
                .body("{\"email\": \"test@example.com\", \"amount\": \"10000\"}")
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(401)
                .body("status", equalTo(false));
    }
    @Test
    public void TC08_shouldReturn400OnZeroAmount() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body("{\"email\": \"test@example.com\", \"amount\": \"0\"}")
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(400)
                .body("status", equalTo(false))
                .body("message", notNullValue());
    }
    @Test
    public void TC09_shouldReturnErrorOnNonExistentReference() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .when()
                .get("/transaction/verify/fake-ref-000000")
                .then()
                .statusCode(400)
                .body("status", equalTo(false))
                .body("message", notNullValue());
    }
    @Test
    public void TC10_shouldReturnErrorOnDuplicateReference() {
        String duplicateReference = "duplicate-ref-" + System.currentTimeMillis();
        String body = "{\"email\": \"test@example.com\", \"amount\": \"10000\", \"reference\": \"" + duplicateReference + "\"}";

        // First request — should succeed
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(200)
                .body("status", equalTo(true));

        // Second request with same reference — should fail
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/transaction/initialize")
                .then()
                .statusCode(400)
                .body("status", equalTo(false));
    }
    @Test
    public void TC11_shouldReturnErrorOnNonExistentReference() {
        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .when()
                .get("/transaction/verify/fake-ref-123")
                .then()
                .statusCode(400)
                .body("status", equalTo(false))
                .body("message", notNullValue());
    }
    @Test
    public void TC12_shouldReturnOnlySuccessfulTransactionsWhenFilteredByStatus() {
        // Fetch transactions filtered by status=success
        io.restassured.response.Response response =
                given()
                        .header("Authorization", "Bearer " + SECRET_KEY)
                        .queryParam("status", "success")
                        .when()
                        .get("/transaction")
                        .then()
                        .statusCode(200)
                        .body("status", equalTo(true))
                        .extract().response();

        // Assert no abandoned transactions in the filtered results
        java.util.List<String> statuses = response.jsonPath().getList("data.status");
        for (String status : statuses) {
            org.testng.Assert.assertEquals(status, "success",
                    "Expected only success transactions but found: " + status);
        }
    }
}
