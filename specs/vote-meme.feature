Feature: Voting on a MEME

  Signed-in USERS vote on a MEME; each USER has ONE VOTE per MEME, worked as a
  toggle: repeating the same VOTE retracts it, the opposite direction switches
  it. An up-voted MEME becomes a higher-scoring MEME in the public hot list.

  Background:
    Given a signed-in USER
    And two uploaded MEMES A and B

  Rule: The MEME with more distinct up-voters ranks higher

    Example:
      When 2 USERS up-vote MEME A
      And 1 USER up-votes MEME B
      Then MEME A ranks above MEME B in the hot list

  Rule: Repeating the same VOTE retracts it

    Example:
      When the USER up-votes MEME A 2 times
      Then MEME A's score is 0 and the USER's vote is gone
      When the USER up-votes MEME A 1 times
      Then MEME A's score is 1

  Rule: Without signing in there is no voting

    Example:
      When an anonymous visitor tries to up-vote MEME A
      Then the request is refused as sign-in required
