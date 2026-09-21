package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.MarkUserContentForErasure;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.application.RekeyUserContent;
import com.jrobertgardzinski.memes.application.RestoreUserContent;
import com.jrobertgardzinski.observation.Observations;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A member changes their e-mail address, and everything this service holds of them follows — over
 * the real web boundary, the real listener and the real schema, because every claim here is a claim
 * about a row and about what an HTTP caller is then allowed to do.
 *
 * <p>What it is for (F-014). The author column holds the address the token carried at upload time,
 * and nothing rewrote it: after a confirmed change of address Alice was a stranger to her own work
 * — {@code own:false}, {@code DELETE} 403 — and the address she left behind carried her authorship
 * to whoever registered it next. The third test is the other half of the same defect and the more
 * expensive one: a deletion saga naming an address this service holds nothing under used to be
 * answered with a confirmation indistinguishable from a real erasure, so the account went and the
 * images stayed.
 *
 * <p>The listeners are built by hand rather than autowired, exactly as
 * {@code PurgeConfirmationOutboxTest} does: they hang off {@code memes.kafka-enabled}, which is off
 * without a broker, and what is under test is the handling of a record and not Kafka's delivery of
 * it. Everything below them — use cases, adapters, transaction manager, Flyway schema — is the
 * application's own.
 */
@Epic("Saga")
@Feature("Address changes")
@Story("The rows follow the member")
@SpringBootTest(classes = {MemesApplication.class, TestAuthConfig.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "memes.upload.rate-limit-per-minute=0")
class RenamedMemberKeepsTheirMemesTest {

    private static final String OLD_ADDRESS = TestAuthConfig.SIGNED_IN_USER;
    private static final String NEW_ADDRESS = TestAuthConfig.RENAMED_USER;
    private static final String SAGA = "3f5c2b71-6a4e-44d9-9c0e-8b1d7f2a4e33";

    private static final String RENAME = "{\"id\":\"2a7f0f5a-1c4b-3e6d-8a9b-0c1d2e3f4a5b\","
            + "\"type\":\"EMAIL_CHANGED\",\"oldEmail\":\"" + OLD_ADDRESS + "\","
            + "\"email\":\"" + NEW_ADDRESS + "\",\"version\":1}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RekeyUserContent rekeyUserContent;

    @Autowired
    MarkUserContentForErasure markForErasure;

    @Autowired
    RestoreUserContent restoreUserContent;

    @Autowired
    PurgeUserContent purgeUserContent;

    @Autowired
    TransactionTemplate tx;

    private SecurityEventsListener renames;
    private String memeId;

    @BeforeEach
    void aliceUploadsAndVotes() throws Exception {
        renames = new SecurityEventsListener(rekeyUserContent, objectMapper, tx);
        memeId = upload(uniquePicture(), TestAuthConfig.VALID_TOKEN);
        // her own ballot, because the votes are keyed by the voter's address too and a ballot left
        // behind is both a vote she cannot change and one the erasure would not find
        vote(memeId, TestAuthConfig.VALID_TOKEN);
    }

    @Test
    @DisplayName("after a confirmed rename the memes and the ballots are the NEW address's — and only its")
    void the_rows_move_to_the_new_address() throws Exception {
        assertTrue(own(memeId, TestAuthConfig.VALID_TOKEN), "before the rename she is the author");

        renames.receive(RENAME, "cid-of-the-change");

        assertTrue(own(memeId, TestAuthConfig.RENAMED_TOKEN),
                "the meme is hers under the address she now has");
        assertFalse(own(memeId, TestAuthConfig.VALID_TOKEN),
                "and not under the one she left behind — that is the address the next registrant"
                        + " would present");
        assertEquals("UP", myVote(memeId, TestAuthConfig.RENAMED_TOKEN),
                "her ballot moved with her");
        assertEquals("none", myVote(memeId, TestAuthConfig.VALID_TOKEN),
                "and is gone from the freed address");
        // the capability, not just the flag: authorisation reads the same column
        mockMvc.perform(delete("/memes/" + memeId)
                        .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/memes/" + memeId)
                        .header("Authorization", "Bearer " + TestAuthConfig.RENAMED_TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a redelivered rename changes nothing — the second UPDATE matches no row")
    void a_redelivered_rename_is_a_no_op() throws Exception {
        renames.receive(RENAME, null);
        renames.receive(RENAME, null);

        assertTrue(own(memeId, TestAuthConfig.RENAMED_TOKEN), "still hers, exactly once");
        assertEquals("UP", myVote(memeId, TestAuthConfig.RENAMED_TOKEN), "and still one ballot");
        assertEquals(0, rekeyUserContent.execute(OLD_ADDRESS, NEW_ADDRESS),
                "a third delivery finds nothing left under the old address: the idempotence is the"
                        + " UPDATE's own, which is why there is no dedup table here");
    }

    @Test
    @DisplayName("a purge naming an address this service holds nothing under confirms a ZERO, not a success")
    void a_purge_that_reserved_nothing_says_so() throws Exception {
        renames.receive(RENAME, null);   // her memes now answer to the new address

        // ...and the saga is commanded for the old one: a deletion requested before the rename, or
        // an orchestrator that has not caught up. Nothing here can tell that from a member who
        // never uploaded anything, which is exactly why the confirmation reports instead of claims
        CapturedConfirmations confirmations = new CapturedConfirmations();
        new PurgeCommandsListener(markForErasure, restoreUserContent, purgeUserContent,
                confirmations, Observations.silent(), objectMapper, tx)
                .receive("{\"type\":\"PURGE_USER_CONTENT\",\"email\":\"" + OLD_ADDRESS + "\","
                        + "\"sagaId\":\"" + SAGA + "\"}", null);

        assertNotNull(confirmations.captured(), "the saga must still get an answer — withholding it"
                + " would fail the deletion of every member who never uploaded anything");
        assertEquals(0, objectMapper.readTree(confirmations.captured().payload()).get("reserved").asInt(),
                "and the answer must not read as an erasure: nothing was reserved, so the"
                        + " confirmation says nothing was reserved");
        // the whole point of the count: the content really is still here
        assertTrue(own(memeId, TestAuthConfig.RENAMED_TOKEN),
                "the memes are still in the gallery, under the address she actually uses");
    }

    private boolean own(String id, String token) throws Exception {
        String body = mockMvc.perform(get("/memes/" + id + "/meta")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("own").asBoolean();
    }

    /** The viewer's own ballot as the tally reports it — {@code "none"} when they have none. */
    private String myVote(String id, String token) throws Exception {
        String body = mockMvc.perform(get("/memes/" + id + "/votes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode mine = objectMapper.readTree(body).get("myVote");
        return mine == null || mine.isNull() ? "none" : mine.asText();
    }

    private void vote(String id, String token) throws Exception {
        mockMvc.perform(post("/memes/" + id + "/votes")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"direction\":\"UP\"}"))
                .andExpect(status().isOk());
    }

    private String upload(byte[] image, String token) throws Exception {
        String body = mockMvc.perform(multipart("/memes")
                        .file(new MockMultipartFile("file", "meme.bmp", "image/bmp", image))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    /** A picture no other test in this run can collide with — dedup is content-addressed. */
    private static byte[] uniquePicture() throws Exception {
        BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, java.util.concurrent.ThreadLocalRandom.current().nextInt());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
