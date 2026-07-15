Feature: Moderating memes

  A MEME belongs to its uploader, who may delete it; a MODERATOR (a role microservice-security
  reports for the caller) may delete anyone's MEME. Everyone else is refused.

  Scenario: a stranger cannot delete someone else's meme
    Given a meme uploaded by its author
    When another user tries to delete it
    Then the deletion is refused as not-theirs
    And the meme can still be fetched

  Scenario: the author deletes their own meme
    Given a meme uploaded by its author
    When the author deletes it
    Then the deletion succeeds as the author
    And the meme is gone

  Scenario: a moderator deletes someone else's meme
    Given a meme uploaded by its author
    When a moderator deletes it
    Then the deletion succeeds as a moderator
    And the meme is gone

  Scenario: deleting without signing in is refused
    Given a meme uploaded by its author
    When an anonymous user tries to delete it
    Then the deletion is refused as sign-in required

  Scenario: a moderator flags a meme NSFW and the gallery carries the flag
    Given a meme uploaded by its author
    When a moderator flags it NSFW
    Then the gallery lists the meme as NSFW
    When a moderator takes the NSFW flag back
    Then the gallery lists the meme as safe

  Scenario: the safe-for-work judgement is a moderator's alone
    Given a meme uploaded by its author
    When the author tries to flag it NSFW
    Then the flagging is refused as not-a-moderator
    And the gallery lists the meme as safe
