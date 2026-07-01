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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP glue for {@code vote-meme.feature}: upload two memes, up-vote them differently, and check the
 * hot ranking orders the more up-voted one first.
 */
public class VoteSteps {

    @LocalServerPort
    int port;

    private String memeA;
    private String memeB;

    @Given("two uploaded memes A and B")
    public void two_uploaded_memes() throws Exception {
        memeA = upload();
        memeB = upload();
    }

    @When("meme {word} gets {int} up-vote(s)")
    public void meme_gets_up_votes(String which, int count) {
        String id = idOf(which);
        for (int i = 0; i < count; i++) {
            RestAssured.given().port(port).contentType("application/json")
                    .body("{\"direction\":\"UP\"}")
                    .post("/memes/" + id + "/votes")
                    .then().statusCode(200);
        }
    }

    @Then("meme {word} ranks above meme {word} in the hot list")
    public void ranks_above(String higher, String lower) {
        Response hot = RestAssured.given().port(port).get("/memes/hot");
        hot.then().statusCode(200);
        List<String> order = hot.jsonPath().getList("memeId");
        assertTrue(order.indexOf(idOf(higher)) < order.indexOf(idOf(lower)),
                "expected " + higher + " to rank above " + lower + ", order was " + order);
    }

    private String idOf(String which) {
        return which.equals("A") ? memeA : memeB;
    }

    private String upload() throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return RestAssured.given().port(port)
                .multiPart("file", "meme.bmp", out.toByteArray(), "image/bmp")
                .post("/memes")
                .jsonPath().getString("id");
    }
}
