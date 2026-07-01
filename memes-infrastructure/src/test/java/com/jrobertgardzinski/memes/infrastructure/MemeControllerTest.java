package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Black-box web test: upload a BMP, then fetch the meme back and check it was served as PNG.
 */
@SpringBootTest
class MemeControllerTest {

    @Autowired
    org.springframework.web.context.WebApplicationContext webApplicationContext;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void uploads_and_serves_an_optimized_meme() throws Exception {
        MockMvc mockMvc = webAppContextSetup(webApplicationContext).build();
        MockMultipartFile file = new MockMultipartFile("file", "meme.bmp", "image/bmp", bmp());

        String body = mockMvc.perform(multipart("/memes").file(file))
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
