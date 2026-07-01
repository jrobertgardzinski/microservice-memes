Feature: Voting on memes

  Up-voted memes rank higher in the hot list.

  Scenario: the more up-voted meme ranks higher
    Given two uploaded memes A and B
    When meme A gets 2 up-votes
    And meme B gets 1 up-vote
    Then meme A ranks above meme B in the hot list
