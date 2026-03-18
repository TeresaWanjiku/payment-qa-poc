import io.github.cdimascio.dotenv.Dotenv;
import io.restassured.RestAssured;
import org.testng.annotations.BeforeClass;

public class BaseTest {

    protected static String SECRET_KEY;

    @BeforeClass
    public void setUp() {
        Dotenv dotenv = Dotenv.configure()
                .directory("C:/Users/user/payment-qa-poc")
                .ignoreIfMissing()
                .load();
        SECRET_KEY = dotenv.get("PAYSTACK_SECRET_KEY");
        RestAssured.baseURI = dotenv.get("BASE_URL");
    }
}