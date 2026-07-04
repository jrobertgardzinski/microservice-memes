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
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP glue for {@code moderate-meme.feature}: the author (alice) uploads; a stranger (bob), a
 * moderator (mod) and an anonymous caller each try to delete. Deletion authority comes from the
 * roles microservice-security reports, stubbed by {@link TestAuthConfig}.
 */
public class ModerationSteps {

    @LocalServerPort
    int port;

    private String memeId;
    private Response lastDelete;

    @Given("a meme uploaded by its author")
    public void aMemeUploadedByItsAuthor() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        image.setRGB(2, 2, ThreadLocalRandom.current().nextInt(0xFFFFFF));   // unique, so dedup never links memes
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        memeId = RestAssured.given().port(port)
                .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN)
                .multiPart("file", "meme.png", png.toByteArray(), "image/png")
                .post("/memes")
                .jsonPath().getString("id");
    }

    @When("another user tries to delete it")
    public void anotherUserDeletes() {
        lastDelete = delete(TestAuthConfig.SECOND_TOKEN);
    }

    @When("the author deletes it")
    public void theAuthorDeletes() {
        lastDelete = delete(TestAuthConfig.VALID_TOKEN);
    }

    @When("a moderator deletes it")
    public void aModeratorDeletes() {
        lastDelete = delete(TestAuthConfig.MODERATOR_TOKEN);
    }

    @When("an anonymous user tries to delete it")
    public void anAnonymousUserDeletes() {
        lastDelete = RestAssured.given().port(port).delete("/memes/{id}", memeId);
    }

    @Then("the deletion is refused as not-theirs")
    public void refusedAsNotTheirs() {
        assertEquals(403, lastDelete.statusCode());
        assertEquals("NOT_YOURS", lastDelete.jsonPath().getString("status"));
    }

    @Then("the deletion is refused as sign-in required")
    public void refusedAsSignInRequired() {
        assertEquals(401, lastDelete.statusCode());
    }

    @Then("the deletion succeeds as the author")
    public void succeedsAsAuthor() {
        assertEquals(200, lastDelete.statusCode());
        assertEquals("AUTHOR", lastDelete.jsonPath().getString("by"));
    }

    @Then("the deletion succeeds as a moderator")
    public void succeedsAsModerator() {
        assertEquals(200, lastDelete.statusCode());
        assertEquals("MODERATOR", lastDelete.jsonPath().getString("by"));
    }

    @Then("the meme can still be fetched")
    public void memeStillThere() {
        assertEquals(200, RestAssured.given().port(port).get("/memes/{id}", memeId).statusCode());
    }

    @Then("the meme is gone")
    public void memeGone() {
        assertEquals(404, RestAssured.given().port(port).get("/memes/{id}", memeId).statusCode());
    }

    private Response delete(String token) {
        return RestAssured.given().port(port)
                .header("Authorization", "Bearer " + token)
                .delete("/memes/{id}", memeId);
    }
}
