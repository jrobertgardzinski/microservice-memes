Feature: The purge-policy default is an ADMIN's dial

  What happens to a leaver's memes is deployment policy — but policy changes
  faster than deployments. An ADMIN may override the env default at runtime; the
  leaver's own wizard choice still wins over everything. Everyone else is
  refused at the door.

  Rule: The ADMIN's override wins over the deployment default, and the purge obeys it

    Example:
      Given the ADMIN sets the memes purge policy to "ANONYMIZE_AUTHOR"
      Then the effective memes purge policy is "ANONYMIZE_AUTHOR" from "DB"
      When a leaver with one MEME is purged without a wizard choice
      Then the leaver's MEME survives anonymised

  Rule: A plain USER may not touch the dial

    Example:
      When a plain USER tries to set the memes purge policy
      Then the policy change is refused as not-an-ADMIN

  Rule: Clearing the override restores the deployment default

    Example:
      Given the ADMIN sets the memes purge policy to "ANONYMIZE_AUTHOR"
      When the ADMIN clears the memes purge-policy override
      Then the effective memes purge policy is "DELETE" from "ENV"
