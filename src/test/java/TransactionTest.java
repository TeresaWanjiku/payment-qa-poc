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
}
