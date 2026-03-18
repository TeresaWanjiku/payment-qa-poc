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
    @Test
    public void TC04_shouldReturnSuccessStatusAfterPaymentCompleted() {
        // Using a reference from a previously completed sandbox payment
        String completedReference = "hgkc6ese08";

        given()
                .header("Authorization", "Bearer " + SECRET_KEY)
                .when()
                .get("/transaction/verify/" + completedReference)
                .then()
                .statusCode(200)
                .body("data.status", equalTo("success"))
                .body("data.gateway_response", equalTo("Approved"))
                .body("data.amount", equalTo(100))
                .body("data.currency", equalTo("KES"));
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
