Feature: Uploading a meme

  Signed-in users upload images; a MEME is stored optimised for the browser. Browsing is public:
  anyone can fetch a MEME, its thumbnail, or the gallery without signing in.

  Nouns:
    MEME -> Meme

  Scenario: a signed-in user uploads, anyone fetches it back
    Given a signed-in user
    When the user uploads a BMP image
    Then the meme is stored
    And fetching it without signing in returns a PNG

  Scenario: fetch a meme's thumbnail
    Given a signed-in user
    When the user uploads a BMP image
    Then fetching its thumbnail returns a PNG

  Scenario: the gallery lists the meme publicly
    Given a signed-in user
    When the user uploads a BMP image
    Then the gallery lists it without signing in

  Scenario: an anonymous upload is refused
    When an anonymous user tries to upload a BMP image
    Then the request is refused as sign-in required
