-- Identities here are the security service's e-mails, and V1 sized them at varchar(200) while
-- security stores VARCHAR(255) and the shared email domain (Email/LocalPart/DomainPart) imposes no
-- length limit at all. Nothing on the path caps the address either: the RFC format constraint uses an
-- unbounded '+', and RequireSignInFilter hands the confirmed address straight to the INSERT. So a
-- registered account with a 220-character address could use comments (VARCHAR(255)) and collections
-- (VARCHAR(320)) normally, and got a 22001 — surfacing as a 500, not a refusal — the moment it tried
-- to publish or vote in the gallery.
--
-- The view has to go first and come back unchanged: Postgres refuses to alter the type of a column a
-- view depends on, even when the change only widens it. The body below is V10's, verbatim.
drop view active_memes;

alter table memes alter column author set data type varchar(255);

create view active_memes as
    select id, author, format, published_at
    from memes
    where status = 'ACTIVE';

alter table meme_votes alter column voter set data type varchar(255);
