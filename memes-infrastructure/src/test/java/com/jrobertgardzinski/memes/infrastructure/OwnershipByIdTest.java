package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ownership over HTTP once the token carries an id: the id outranks the address in both
 * directions, and a token without one still gets the address rule.
 */
@Epic("Infrastructure")
@Feature("Ownership by id")
@SpringBootTest(classes = {MemesApplication.class, TestAuthConfig.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "memes.upload.rate-limit-per-minute=0")
class OwnershipByIdTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("the same id under a new address still owns the meme; the same address under another id does not")
    void the_id_outranks_the_address() throws Exception {
        String memeId = upload(TestAuthConfig.ALICE_ID_TOKEN);

        assertTrue(own(memeId, TestAuthConfig.ALICE_ID_RENAMED_TOKEN), "same id, new address");
        assertFalse(own(memeId, TestAuthConfig.IMPOSTOR_TOKEN), "same address, another id");
        assertTrue(own(memeId, TestAuthConfig.VALID_TOKEN), "a token without an id falls back to the address");

        mockMvc.perform(delete("/memes/" + memeId)
                        .header("Authorization", "Bearer " + TestAuthConfig.IMPOSTOR_TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/memes/" + memeId)
                        .header("Authorization", "Bearer " + TestAuthConfig.ALICE_ID_RENAMED_TOKEN))
                .andExpect(status().isOk());
    }

    private boolean own(String id, String token) throws Exception {
        String body = mockMvc.perform(get("/memes/" + id + "/meta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("own").asBoolean();
    }

    private String upload(String token) throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, java.util.concurrent.ThreadLocalRandom.current().nextInt());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        String body = mockMvc.perform(multipart("/memes")
                        .file(new MockMultipartFile("file", "meme.bmp", "image/bmp", out.toByteArray()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
