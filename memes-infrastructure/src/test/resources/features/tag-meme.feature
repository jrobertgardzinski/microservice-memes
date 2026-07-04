Feature: Tagging memes and finding them by tag

  The uploader curates their meme's tags — a small, legal set of search keys, not free text.
  Anyone browses the gallery narrowed to one tag; tags of a purged meme vanish with it.

  Scenario: the author tags their meme and the gallery finds it by tag
    Given a signed-in user
    And an uploaded meme
    When the author tags it with "cats" and "monday-mood"
    Then the gallery filtered by "cats" contains that meme
    And the gallery filtered by "dogs" does not

  Scenario: only the author curates the tags
    Given a signed-in user
    And an uploaded meme
    When another user tries to tag it with "hijack"
    Then the tagging is refused as not-the-author

  Scenario: keyword spam is refused
    Given a signed-in user
    And an uploaded meme
    When the author tags it with "not a tag!" and "monday-mood"
    Then the tagging is refused as an invalid tag
