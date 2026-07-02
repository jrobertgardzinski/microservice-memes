Feature: Commenting on a meme

  Signed-in users comment on memes; the comment is signed by the identity that
  microservice-security confirms for their token — the author is never taken from the request.
  Reading comments is public.

  Scenario: a signed-in user comments on an existing meme
    Given a signed-in user
    And an uploaded meme
    When the user comments "great meme"
    Then the comment appears in the meme's comments, signed by the user

  Scenario: comments can be voted on; repeating the vote retracts it
    Given a signed-in user
    And an uploaded meme
    When the user comments "great meme"
    And another user up-votes that comment
    Then the comment's score is 1
    When the other user up-votes that comment again
    Then the comment's score is 0

  Scenario: an anonymous comment is refused, but reading stays public
    Given a signed-in user
    And an uploaded meme
    When an anonymous user tries to comment "drive-by"
    Then the request is refused as sign-in required
    And the meme's comments can be read without signing in
