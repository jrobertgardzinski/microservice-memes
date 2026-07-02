package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CastVote;
import com.jrobertgardzinski.memes.application.RankMemes;
import com.jrobertgardzinski.memes.application.ShowMemeVote;
import com.jrobertgardzinski.voting.VoteDirection;
import com.jrobertgardzinski.voting.VoteTally;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web boundary for voting on memes (comment voting lives in microservice-comments). The toggle
 * semantics come from the voting library; responses carry the caller's resulting choice
 * ({@code myVote}), so the UI can render the pressed arrow. The hot list and the tally GET are
 * public.
 */
@RestController
@RequestMapping("/memes")
class VoteController {

    /** What a client posts to vote: {@code direction} is UP or DOWN (case-insensitive). */
    record VoteRequest(String direction) {}

    private final CastVote castVote;
    private final ShowMemeVote showMemeVote;
    private final RankMemes rankMemes;

    VoteController(CastVote castVote, ShowMemeVote showMemeVote, RankMemes rankMemes) {
        this.castVote = castVote;
        this.showMemeVote = showMemeVote;
        this.rankMemes = rankMemes;
    }

    @PostMapping("/{memeId}/votes")
    ResponseEntity<?> voteOnMeme(@PathVariable("memeId") String memeId,
                                 @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER) String voter,
                                 @RequestBody VoteRequest request) {
        Optional<VoteDirection> direction = parseDirection(request);
        if (direction.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_DIRECTION"));
        }
        return toResponse(castVote.execute(memeId, voter, direction.get()));
    }

    @GetMapping("/{memeId}/votes")
    ResponseEntity<?> memeTally(@PathVariable("memeId") String memeId,
                                @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_USER, required = false)
                                String viewer) {
        return toResponse(showMemeVote.execute(memeId, Optional.ofNullable(viewer)));
    }

    @GetMapping("/hot")
    List<Map<String, Object>> hot() {
        return rankMemes.execute().stream()
                .map(ranked -> Map.<String, Object>of("memeId", ranked.memeId(), "score", ranked.score()))
                .toList();
    }

    private static Optional<VoteDirection> parseDirection(VoteRequest request) {
        try {
            return Optional.of(VoteDirection.valueOf(String.valueOf(request.direction()).trim().toUpperCase()));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<?> toResponse(Optional<VoteTally> tally) {
        if (tally.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new HashMap<>();
        body.put("score", tally.get().score());
        body.put("myVote", tally.get().voterChoice().map(Enum::name).orElse(null));
        return ResponseEntity.ok(body);
    }
}
