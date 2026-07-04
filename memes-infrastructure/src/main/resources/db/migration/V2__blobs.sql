-- Image bytes move out of the meme row into their own store, behind the ObjectStore port, so they
-- can later live in object storage (S3/MinIO) without touching the metadata. The default store is
-- this table; existing bytes are carried over before the column is dropped.

create table meme_blobs (
    object_key varchar(36) primary key,
    data       bytea       not null
);

insert into meme_blobs (object_key, data) select id, data from memes;

alter table memes drop column data;
