package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CastVote;
import com.jrobertgardzinski.memes.application.RankMemes;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Web boundary for voting: cast a vote on a meme, and list the hottest memes. The boundary parses
 * raw input into domain types (bad direction → 400), so the use case only sees valid votes.
 */
@RestController
@RequestMapping("/memes")
class VoteController {

    /** What a client posts to vote on a meme: {@code direction} is UP or DOWN (case-insensitive). */
    record VoteRequest(String direction) {}

    private final CastVote castVote;
    private final RankMemes rankMemes;

    VoteController(CastVote castVote, RankMemes rankMemes) {
        this.castVote = castVote;
        this.rankMemes = rankMemes;
    }

    @PostMapping("/{memeId}/votes")
    ResponseEntity<?> vote(@PathVariable("memeId") String memeId, @RequestBody VoteRequest request) {
        VoteDirection direction;
        try {
            direction = VoteDirection.valueOf(String.valueOf(request.direction()).trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_DIRECTION"));
        }
        return castVote.execute(memeId, direction)
                .<ResponseEntity<?>>map(score -> ResponseEntity.ok(Map.of("score", score)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/hot")
    List<Map<String, Object>> hot() {
        return rankMemes.execute().stream()
                .map(ranked -> Map.<String, Object>of("memeId", ranked.memeId(), "score", ranked.score()))
                .toList();
    }
}
