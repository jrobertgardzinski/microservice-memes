package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box web test: upload a BMP (signed in via the stubbed gate), then fetch the meme back
 * anonymously and check it was served as PNG.
 */
@SpringBootTest(classes = {MemesApplication.class, TestAuthConfig.class})
@AutoConfigureMockMvc
class MemeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void uploads_and_serves_an_optimized_meme() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "meme.bmp", "image/bmp", bmp());

        String body = mockMvc.perform(multipart("/memes").file(file)
                        .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        byte[] png = mockMvc.perform(get("/memes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals((byte) 0x89, png[0]); // PNG magic
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
