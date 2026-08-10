Feature: An account deletion is a SAGA, so hiding comes first and erasing comes last

  A meme service that shreds a leaver's pictures the moment the PURGE command
  arrives leaves the ORCHESTRATOR with nothing to undo when a LATER participant
  of the same SAGA fails — and that is not a theoretical worry: it is what used
  to happen, and the leaver got their account back without their memes and an
  e-mail apologising for a deletion that had not, in fact, been cancelled.

  So the meme service answers the PURGE by MARKING: the memes leave the gallery
  at once — the whole of what the leaver asked to see — and stay on disk,
  restorable, until the ORCHESTRATOR says the case is settled. Only its closure
  command erases anything, and the image leaving object storage is the point
  past which nothing can be taken back (ADR 0007).

  Background:
    Given a leaver with one MEME in the gallery

  Rule: A failure at another participant brings the leaver's memes back

    Example:
      When the ORCHESTRATOR commands the PURGE of their content
      Then the MEME is gone from the gallery
      But the MEME is still stored
      When the comments service never confirms and the ORCHESTRATOR compensates
      Then the MEME is back in the gallery
      And the MEME is still stored

  Rule: The closure is the point of no return — the memes are erased for good

    Example:
      When the ORCHESTRATOR commands the PURGE of their content
      And every participant confirms and the ORCHESTRATOR closes the SAGA
      Then the MEME is gone from the gallery
      And the image is gone from object storage
      And a late compensation brings nothing back

  Rule: The PURGE command arriving twice, as Kafka promises it may, changes nothing

    Example:
      When the ORCHESTRATOR commands the PURGE of their content
      And the ORCHESTRATOR commands the PURGE of their content
      Then the MEME is gone from the gallery
      And the MEME is still stored
      When the comments service never confirms and the ORCHESTRATOR compensates
      Then the MEME is back in the gallery
