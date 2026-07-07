Feature: The purge-policy default is an administrator's dial

  What happens to a leaver's memes is deployment policy — but policy changes faster than
  deployments. An ADMIN may override the env default at runtime; the leaver's own wizard
  choice still wins over everything. Everyone else is refused at the door.

  Scenario: an admin overrides the deployment default and the purge obeys it
    Given the admin sets the memes purge policy to "ANONYMIZE_AUTHOR"
    Then the effective memes purge policy is "ANONYMIZE_AUTHOR" from "DB"
    When a leaver with one meme is purged without a wizard choice
    Then the leaver's meme survives anonymised

  Scenario: a plain user may not touch the dial
    When a plain user tries to set the memes purge policy
    Then the policy change is refused as not-an-admin

  Scenario: clearing the override restores the deployment default
    Given the admin sets the memes purge policy to "ANONYMIZE_AUTHOR"
    When the admin clears the memes purge-policy override
    Then the effective memes purge policy is "DELETE" from "ENV"
