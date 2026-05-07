import io.github.cdimascio.dotenv.Dotenv;
import io.restassured.RestAssured;
import org.testng.annotations.BeforeClass;

public class BaseTest {

    protected static String SECRET_KEY;

    @BeforeClass
    public void setUp() {
        if (System.getenv("CI") != null) {
            SECRET_KEY = System.getenv("PAYSTACK_SECRET_KEY");
            RestAssured.baseURI = System.getenv("BASE_URL");
        } else {
            Dotenv dotenv = Dotenv.configure()
                    .directory("C:/Projects/payment-qa-poc")
                    .ignoreIfMissing()
                    .load();
            SECRET_KEY = dotenv.get("PAYSTACK_SECRET_KEY");
            RestAssured.baseURI = dotenv.get("BASE_URL");
        }
    }
}