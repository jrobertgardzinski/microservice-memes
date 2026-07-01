Feature: Commenting on a meme

  A user comments on a meme and sees the comment listed under it.

  Scenario: comment on an existing meme
    Given an uploaded meme
    When a user comments "great meme" as "alice"
    Then the comment appears in the meme's comments
