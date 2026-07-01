package com.jrobertgardzinski.memes.infrastructure.cucumber;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP glue for {@code comment-meme.feature}: upload a meme, comment on it, list the comments.
 */
public class CommentSteps {

    @LocalServerPort
    int port;

    private String memeId;

    @Given("an uploaded meme")
    public void an_uploaded_meme() throws Exception {
        Response uploaded = RestAssured.given().port(port)
                .multiPart("file", "meme.bmp", bmp(), "image/bmp")
                .post("/memes");
        memeId = uploaded.jsonPath().getString("id");
    }

    @When("a user comments {string} as {string}")
    public void a_user_comments(String text, String author) {
        RestAssured.given().port(port)
                .contentType("application/json")
                .body("{\"author\":\"" + author + "\",\"text\":\"" + text + "\"}")
                .post("/memes/" + memeId + "/comments")
                .then().statusCode(201);
    }

    @Then("the comment appears in the meme's comments")
    public void the_comment_appears() {
        Response listed = RestAssured.given().port(port).get("/memes/" + memeId + "/comments");
        listed.then().statusCode(200);
        List<Object> comments = listed.jsonPath().getList("$");
        assertEquals(1, comments.size());
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
