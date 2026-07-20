Feature: Leaving — deleting the account from the gallery
  The danger zone in the panel is the RODO exit, and it is deliberately not a single click: the
  visitor says what should happen to what they posted, then proves it is really them (step-up —
  a stolen session must not be able to end an account), and only then does the account go.

  NOTE ON SCOPE: this harness runs without Kafka, so security is started in its identity-only
  mode (account-deletion.await-portal-purge=false) and these scenarios pin the IDENTITY half of
  the exit — the wizard, the step-up, the account really being gone. The content half (the saga
  that has memes and comments purge per the chosen policy) is proven where it lives: the purge
  use cases' own tests in memes and comments, and the live stack's infra-smoke.

  Scenario: Deleting the account needs the password, then it is really gone
    Given a verified account exists
    And the visitor opens the gallery
    And signs in with that account
    When they open the danger zone
    And confirm the deletion with their password
    Then the panel says the deletion started
    And the panel does not show them as signed in
    And signing in with that account is refused

  Scenario: The wrong password does not end an account
    Given a verified account exists
    And the visitor opens the gallery
    And signs in with that account
    When they open the danger zone
    And confirm the deletion with the wrong password
    Then the dialog complains about the password
    And signing in with that account still works

  Scenario: Second thoughts leave everything alone
    Given a verified account exists
    And the visitor opens the gallery
    And signs in with that account
    When they open the danger zone
    And keep their account instead
    Then the panel shows they are signed in
    And signing in with that account still works

  Scenario: A second factor is asked for on the way out too
    Given a verified account exists
    And the account carries a mailed sign-in code factor
    And the visitor opens the gallery
    And signs in with that account through the code step
    When they open the danger zone
    And confirm the deletion with their password
    Then the dialog asks for the sign-in code
    When they confirm the deletion with the code from their mail
    Then the panel says the deletion started
    And signing in with that account is refused
