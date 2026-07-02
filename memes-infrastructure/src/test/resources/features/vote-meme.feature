Feature: Voting on memes

  Signed-in users vote; each user has ONE vote per meme, worked as a toggle: repeating the same
  vote retracts it, the opposite direction switches it. Up-voted memes rank higher in the public
  hot list.

  Scenario: the meme with more distinct up-voters ranks higher
    Given a signed-in user
    And two uploaded memes A and B
    When 2 users up-vote meme A
    And 1 user up-votes meme B
    Then meme A ranks above meme B in the hot list

  Scenario: repeating the same vote retracts it
    Given a signed-in user
    And two uploaded memes A and B
    When the user up-votes meme A 2 times
    Then meme A's score is 0 and the user's vote is gone
    When the user up-votes meme A 1 times
    Then meme A's score is 1

  Scenario: an anonymous vote is refused
    Given a signed-in user
    And two uploaded memes A and B
    When an anonymous user tries to up-vote meme A
    Then the request is refused as sign-in required
