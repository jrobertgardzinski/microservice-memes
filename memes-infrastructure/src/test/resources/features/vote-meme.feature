Feature: Voting on memes

  Signed-in users vote; up-voted memes rank higher in the hot list. The hot list itself is public.

  Scenario: the more up-voted meme ranks higher
    Given a signed-in user
    And two uploaded memes A and B
    When meme A gets 2 up-votes
    And meme B gets 1 up-vote
    Then meme A ranks above meme B in the hot list

  Scenario: an anonymous vote is refused
    Given a signed-in user
    And two uploaded memes A and B
    When an anonymous user tries to up-vote meme A
    Then the request is refused as sign-in required
