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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HTTP glue for {@code comment-meme.feature}: a signed-in user uploads a meme and comments on it;
 * the comment carries the identity confirmed by the (stubbed) security gate, and reading the
 * comments needs no token.
 */
public class CommentSteps {

    @LocalServerPort
    int port;

    private String memeId;
    private String commentId;

    @Given("an uploaded meme")
    public void anUploadedMeme() throws Exception {
        Response uploaded = RestAssured.given().port(port)
                .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN)
                .multiPart("file", "meme.bmp", bmp(), "image/bmp")
                .post("/memes");
        memeId = uploaded.jsonPath().getString("id");
    }

    @When("the user comments {string}")
    public void theUserComments(String text) {
        Response response = RestAssured.given().port(port)
                .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN)
                .contentType("application/json")
                .body("{\"text\":\"" + text + "\"}")
                .post("/memes/" + memeId + "/comments");
        response.then().statusCode(201);
        commentId = response.jsonPath().getString("id");
    }

    @When("another user up-votes that comment twice")
    public void anotherUserUpVotesThatCommentTwice() {
        for (int i = 0; i < 2; i++) {
            RestAssured.given().port(port)
                    .header("Authorization", "Bearer " + TestAuthConfig.SECOND_TOKEN)
                    .contentType("application/json")
                    .body("{\"direction\":\"UP\"}")
                    .post("/memes/" + memeId + "/comments/" + commentId + "/votes")
                    .then().statusCode(200);
        }
    }

    @Then("the comment's score is {int}")
    public void theCommentsScoreIs(int score) {
        Response listed = RestAssured.given().port(port).get("/memes/" + memeId + "/comments");
        listed.then().statusCode(200);
        Map<String, Object> comment = listed.jsonPath().getList("$", Map.class).stream()
                .filter(c -> commentId.equals(((Map<String, Object>) c).get("id")))
                .map(c -> (Map<String, Object>) c)
                .findFirst().orElse(null);
        assertNotNull(comment, "voted comment not listed");
        assertEquals(score, comment.get("score"));
    }

    @When("an anonymous user tries to comment {string}")
    public void anAnonymousUserTriesToComment(String text) {
        AuthSteps.lastAnonymousAttempt = RestAssured.given().port(port)
                .contentType("application/json")
                .body("{\"text\":\"" + text + "\"}")
                .post("/memes/" + memeId + "/comments");
    }

    @Then("the comment appears in the meme's comments, signed by the user")
    public void theCommentAppearsSignedByTheUser() {
        Response listed = RestAssured.given().port(port).get("/memes/" + memeId + "/comments");
        listed.then().statusCode(200);
        List<Map<String, String>> comments = listed.jsonPath().getList("$");
        assertEquals(1, comments.size());
        assertEquals(TestAuthConfig.SIGNED_IN_USER, comments.get(0).get("author"));
    }

    @Then("the meme's comments can be read without signing in")
    public void theCommentsCanBeReadAnonymously() {
        Response listed = RestAssured.given().port(port).get("/memes/" + memeId + "/comments");
        listed.then().statusCode(200);
        // the refused comment was not stored (dedup may reuse a meme from another scenario,
        // so assert on content, not on count)
        List<Map<String, String>> comments = listed.jsonPath().getList("$");
        assertEquals(0, comments.stream().filter(c -> "drive-by".equals(c.get("text"))).count());
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, ThreadLocalRandom.current().nextInt()); // unique content -> no dedup across scenarios
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
