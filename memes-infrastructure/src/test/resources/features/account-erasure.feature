Feature: An account deletion is a saga, so hiding comes first and erasing comes last

  A meme service that shreds a leaver's pictures the moment the purge command arrives leaves the
  orchestrator with nothing to undo when a LATER participant of the same saga fails — and that is
  not a theoretical worry: it is what used to happen, and the leaver got their account back
  without their memes and an e-mail apologising for a deletion that had not, in fact, been
  cancelled.

  So the meme service answers the purge command by MARKING: the memes leave the gallery at once —
  which is the whole of what the leaver asked to see — and stay on disk, restorable, until the
  orchestrator says the case is settled. Only its closure command erases anything, and the image
  leaving object storage is the point past which nothing can be taken back (ADR 0007).

  Scenario: The saga fails at another participant, so the leaver's memes come back
    Given a leaver with one meme in the gallery
    When the orchestrator commands the purge of their content
    Then the meme is gone from the gallery
    But the meme is still stored
    When the comments service never confirms and the orchestrator compensates
    Then the meme is back in the gallery
    And the meme is still stored

  Scenario: The saga closes, so the memes are erased for good
    Given a leaver with one meme in the gallery
    When the orchestrator commands the purge of their content
    And every participant confirms and the orchestrator closes the saga
    Then the meme is gone from the gallery
    And the image is gone from object storage
    And a late compensation brings nothing back

  Scenario: The purge command arrives twice, as Kafka promises it may
    Given a leaver with one meme in the gallery
    When the orchestrator commands the purge of their content
    And the orchestrator commands the purge of their content
    Then the meme is gone from the gallery
    And the meme is still stored
    When the comments service never confirms and the orchestrator compensates
    Then the meme is back in the gallery
