Feature: Moderating memes

  A MEME belongs to its uploader, who may delete it; a MODERATOR (a role microservice-security
  reports for the caller) may delete anyone's MEME. Everyone else is refused.

  Nouns:
    MEME -> Meme

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
