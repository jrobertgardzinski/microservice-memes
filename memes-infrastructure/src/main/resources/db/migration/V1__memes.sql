-- The gallery, durable: metadata and image bytes in one place, the tag and content indexes
-- beside them, one row per (meme, voter) ballot. Identities are the security service's e-mails.

create table memes (
    id           varchar(36)  primary key,
    author       varchar(200) not null,
    format       varchar(10)  not null,
    data         bytea        not null,
    published_at timestamp    not null
);

create table content_index (
    content_hash varchar(64) primary key,
    meme_id      varchar(36) not null
);

create table meme_tags (
    meme_id varchar(36) not null,
    tag     varchar(30) not null,
    primary key (meme_id, tag)
);

create table meme_votes (
    meme_id   varchar(36)  not null,
    voter     varchar(200) not null,
    direction varchar(4)   not null,
    primary key (meme_id, voter)
);
