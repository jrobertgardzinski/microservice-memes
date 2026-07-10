Feature: Favourites — the gallery integrated with user-collections
  A signed-in visitor stars memes; the refs live in microservice-user-collections (opaque ids,
  saved cross-origin straight from the browser) and the gallery hydrates them back into tiles.
  A ref outlives its meme by design — the favourites wall then shows an unavailable keepsake.

  Scenario: A starred meme lands on the favourites wall
    Given a meme has been uploaded by someone
    And a verified account exists
    And the visitor opens the gallery
    And signs in with that account
    When they favourite the meme
    And switch to the favourites wall
    Then the meme's tile is on the wall

  Scenario: Unstarring lets the favourite go
    Given a meme has been uploaded by someone
    And a verified account exists
    And the visitor opens the gallery
    And signs in with that account
    When they favourite the meme
    And switch to the favourites wall
    And unfavourite the meme
    Then the favourites wall is empty

  Scenario: A favourite outliving its meme shows as an unavailable keepsake
    Given a verified account exists
    And the visitor opens the gallery
    And signs in with that account
    When they upload an image
    And they favourite their own meme
    And the meme is deleted behind the gallery's back
    And switch to the favourites wall
    Then the favourites wall shows an unavailable keepsake
