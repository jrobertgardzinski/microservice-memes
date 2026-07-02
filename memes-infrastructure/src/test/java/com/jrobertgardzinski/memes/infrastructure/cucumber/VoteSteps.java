package com.jrobertgardzinski.memes.infrastructure.cucumber;

import com.jrobertgardzinski.memes.infrastructure.TestAuthConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP glue for {@code vote-meme.feature}: a signed-in user uploads two memes and up-votes them
 * differently; the public hot ranking orders the more up-voted one first, and anonymous votes are
 * refused.
 */
public class VoteSteps {

    @LocalServerPort
    int port;

    private String memeA;
    private String memeB;

    @Given("two uploaded memes A and B")
    public void twoUploadedMemes() throws Exception {
        // distinct sizes -> distinct content, so deduplication keeps them as two memes
        memeA = upload(6, 4);
        memeB = upload(8, 5);
    }

    @When("meme {word} gets {int} up-vote(s)")
    public void memeGetsUpVotes(String which, int count) {
        String id = idOf(which);
        for (int i = 0; i < count; i++) {
            RestAssured.given().port(port)
                    .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN)
                    .contentType("application/json")
                    .body("{\"direction\":\"UP\"}")
                    .post("/memes/" + id + "/votes")
                    .then().statusCode(200);
        }
    }

    @When("an anonymous user tries to up-vote meme {word}")
    public void anAnonymousUserTriesToUpVote(String which) {
        AuthSteps.lastAnonymousAttempt = RestAssured.given().port(port)
                .contentType("application/json")
                .body("{\"direction\":\"UP\"}")
                .post("/memes/" + idOf(which) + "/votes");
    }

    @Then("meme {word} ranks above meme {word} in the hot list")
    public void ranksAbove(String higher, String lower) {
        Response hot = RestAssured.given().port(port).get("/memes/hot");
        hot.then().statusCode(200);
        List<String> order = hot.jsonPath().getList("memeId");
        assertTrue(order.indexOf(idOf(higher)) < order.indexOf(idOf(lower)),
                "expected " + higher + " to rank above " + lower + ", order was " + order);
    }

    private String idOf(String which) {
        return which.equals("A") ? memeA : memeB;
    }

    private String upload(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return RestAssured.given().port(port)
                .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN)
                .multiPart("file", "meme.bmp", out.toByteArray(), "image/bmp")
                .post("/memes")
                .jsonPath().getString("id");
    }
}
