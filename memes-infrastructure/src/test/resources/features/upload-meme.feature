Feature: Uploading a meme

  A user uploads an image; it is stored optimised for the browser and can be fetched back as PNG.

  Scenario: upload and fetch a meme
    When a user uploads a BMP image
    Then the meme is stored
    And fetching it returns a PNG

  Scenario: fetch a meme's thumbnail
    When a user uploads a BMP image
    Then fetching its thumbnail returns a PNG
