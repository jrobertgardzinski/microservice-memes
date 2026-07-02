package com.jrobertgardzinski.memes.infrastructure.cucumber;

import com.jrobertgardzinski.memes.infrastructure.TestAuthConfig;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP glue for {@code upload-meme.feature}, black-box against the running Spring app: a signed-in
 * user uploads a BMP; fetching (meme, thumbnail, gallery) deliberately carries no token — reads
 * are public.
 */
public class UploadMemeSteps {

    @LocalServerPort
    int port;

    private Response uploadResponse;
    private String memeId;

    @When("the user uploads a BMP image")
    public void theUserUploadsABmpImage() throws Exception {
        uploadResponse = RestAssured.given().port(port)
                .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN)
                .multiPart("file", "meme.bmp", bmp(), "image/bmp")
                .post("/memes");
        memeId = uploadResponse.jsonPath().getString("id");
    }

    @When("an anonymous user tries to upload a BMP image")
    public void anAnonymousUserTriesToUpload() throws Exception {
        AuthSteps.lastAnonymousAttempt = RestAssured.given().port(port)
                .multiPart("file", "meme.bmp", bmp(), "image/bmp")
                .post("/memes");
    }

    @Then("the meme is stored")
    public void theMemeIsStored() {
        assertEquals(201, uploadResponse.statusCode());
        assertNotNull(memeId, "no meme id returned");
    }

    @Then("fetching it without signing in returns a PNG")
    public void fetchingItAnonymouslyReturnsAPng() {
        Response fetched = RestAssured.given().port(port).get("/memes/" + memeId);
        assertEquals(200, fetched.statusCode());
        assertEquals("image/png", fetched.contentType());
        assertEquals((byte) 0x89, fetched.getBody().asByteArray()[0]); // PNG magic
    }

    @Then("fetching its thumbnail returns a PNG")
    public void fetchingItsThumbnailReturnsAPng() {
        Response thumbnail = RestAssured.given().port(port).get("/memes/" + memeId + "/thumbnail");
        assertEquals(200, thumbnail.statusCode());
        assertEquals("image/png", thumbnail.contentType());
        assertEquals((byte) 0x89, thumbnail.getBody().asByteArray()[0]); // PNG magic
    }

    @Then("the gallery lists it without signing in")
    public void theGalleryListsIt() {
        Response gallery = RestAssured.given().port(port).get("/memes");
        assertEquals(200, gallery.statusCode());
        List<String> ids = gallery.jsonPath().getList("id");
        assertTrue(ids.contains(memeId), "expected " + memeId + " in the gallery, got " + ids);
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
