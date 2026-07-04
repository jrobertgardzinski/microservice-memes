package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Moderation gate on delete: the author removes their own meme, a stranger cannot, and a MODERATOR
 * (its roles come from microservice-security's /me, stubbed here) removes anyone's.
 */
@SpringBootTest(classes = {MemesApplication.class, TestAuthConfig.class})
@AutoConfigureMockMvc
class ModerationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("the author deletes their own; a stranger is refused; a moderator deletes anyone's")
    void moderation_delete() throws Exception {
        String own = upload(TestAuthConfig.VALID_TOKEN);

        // a stranger cannot delete alice's meme
        mockMvc.perform(delete("/memes/{id}", own).header("Authorization", "Bearer " + TestAuthConfig.SECOND_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("NOT_YOURS"));

        // the author can
        mockMvc.perform(delete("/memes/{id}", own).header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.by").value("AUTHOR"));
        mockMvc.perform(get("/memes/{id}", own)).andExpect(status().isNotFound());

        // a moderator can delete someone else's (bob's) meme
        String bobs = upload(TestAuthConfig.SECOND_TOKEN);
        mockMvc.perform(delete("/memes/{id}", bobs).header("Authorization", "Bearer " + TestAuthConfig.MODERATOR_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.by").value("MODERATOR"));
        mockMvc.perform(get("/memes/{id}", bobs)).andExpect(status().isNotFound());

        // deleting without a token is refused (a write needs sign-in)
        mockMvc.perform(delete("/memes/{id}", "ghost")).andExpect(status().isUnauthorized());
    }

    private String upload(String token) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, ThreadLocalRandom.current().nextInt(0xFFFFFF));
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        String body = mockMvc.perform(multipart("/memes")
                        .file(new MockMultipartFile("file", "m.png", "image/png", png.toByteArray()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
