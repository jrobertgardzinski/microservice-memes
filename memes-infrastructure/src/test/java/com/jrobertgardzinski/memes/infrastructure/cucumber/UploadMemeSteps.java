package com.jrobertgardzinski.memes.infrastructure.cucumber;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HTTP glue for {@code upload-meme.feature}, black-box against the running Spring app: upload a BMP,
 * then fetch the meme back and check it is served as PNG.
 */
public class UploadMemeSteps {

    @LocalServerPort
    int port;

    private Response uploadResponse;
    private String memeId;

    @When("a user uploads a BMP image")
    public void a_user_uploads_a_bmp_image() throws Exception {
        uploadResponse = RestAssured.given().port(port)
                .multiPart("file", "meme.bmp", bmp(), "image/bmp")
                .post("/memes");
        memeId = uploadResponse.jsonPath().getString("id");
    }

    @Then("the meme is stored")
    public void the_meme_is_stored() {
        assertEquals(201, uploadResponse.statusCode());
        assertNotNull(memeId, "no meme id returned");
    }

    @Then("fetching its thumbnail returns a PNG")
    public void fetching_its_thumbnail_returns_a_png() {
        Response thumbnail = RestAssured.given().port(port).get("/memes/" + memeId + "/thumbnail");
        assertEquals(200, thumbnail.statusCode());
        assertEquals("image/png", thumbnail.contentType());
        assertEquals((byte) 0x89, thumbnail.getBody().asByteArray()[0]); // PNG magic
    }

    @And("fetching it returns a PNG")
    public void fetching_it_returns_a_png() {
        Response fetched = RestAssured.given().port(port).get("/memes/" + memeId);
        assertEquals(200, fetched.statusCode());
        assertEquals("image/png", fetched.contentType());
        byte[] body = fetched.getBody().asByteArray();
        assertEquals((byte) 0x89, body[0]); // PNG magic
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
