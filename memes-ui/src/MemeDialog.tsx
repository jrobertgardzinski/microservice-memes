import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogContent from '@mui/material/DialogContent';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import VisibilityIcon from '@mui/icons-material/Visibility';
import {
  COMMENTS_PAGE_SIZE, deleteComment, deleteMeme, listComments, memeMeta, memeTags, memeTally,
  MemeComment, postComment, setCommentHidden, setMemeNsfw, setMemeTags, voteOnComment, voteOnMeme,
  VoteDirection, VoteTally,
} from './api';

interface Props {
  memeId: string;
  token: string | null;
  isModerator: boolean;
  onVoted: () => void;
  onRequireSignIn: () => void;
  onTagClick: (tag: string) => void;
  onDeleted: () => void;
  onClose: () => void;
}

/** Arrows with the caller's current vote pressed; clicking the pressed one retracts (the API toggles). */
function VoteButtons({ myVote, onVote }: { myVote: VoteDirection | null; onVote: (d: VoteDirection) => void }) {
  return (
    <>
      <IconButton size="small" aria-label="vote up" onClick={() => onVote('UP')}
                  color={myVote === 'UP' ? 'primary' : 'default'}
                  sx={myVote === 'UP' ? { bgcolor: 'primary.dark' } : undefined}>
        <ArrowUpwardIcon fontSize="inherit" />
      </IconButton>
      <IconButton size="small" aria-label="vote down" onClick={() => onVote('DOWN')}
                  color={myVote === 'DOWN' ? 'error' : 'default'}
                  sx={myVote === 'DOWN' ? { bgcolor: 'error.dark' } : undefined}>
        <ArrowDownwardIcon fontSize="inherit" />
      </IconButton>
    </>
  );
}

/**
 * How far forward the dialog is willing to walk a thread to find a comment somebody just posted.
 * A hundred per page, so this covers a two-thousand-comment thread — and it is a CEILING, not an
 * expectation: the loop stops the moment the comment shows up.
 */
const MAX_THREAD_PAGES = 20;

export default function MemeDialog({ memeId, token, isModerator, onVoted, onRequireSignIn, onTagClick, onDeleted, onClose }: Props) {
  // score null renders "n/a" — the honest answer before the first read has landed, and after one
  // that failed; the old `{ score: 0 }` claimed a tally nobody had reported yet
  const [tally, setTally] = useState<VoteTally>({ score: null, myVote: null });
  const [comments, setComments] = useState<MemeComment[]>([]);
  // the thread is served oldest-first, one page at a time — this is how much of it is on screen
  const [pagesLoaded, setPagesLoaded] = useState(0);
  const [moreComments, setMoreComments] = useState(false);
  const [threadError, setThreadError] = useState(false);
  const [busyThread, setBusyThread] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [editingTags, setEditingTags] = useState(false);
  const [tagDraft, setTagDraft] = useState('');
  const [tagError, setTagError] = useState<string | null>(null);
  // the server's word that the signed-in viewer uploaded this meme — the ONLY basis for the
  // Delete affordance, since /meta masks the uploader and no longer hands out an address to
  // compare against (same contract as a comment's `own`)
  const [own, setOwn] = useState(false);
  const [nsfw, setNsfw] = useState(false);

  /**
   * The thread from the top, and — when `wantedId` is given — as far forward as it takes to bring
   * that comment into view.
   *
   * The listing is ordered by created_at ASCENDING and paged, so on a long thread page 0 is
   * ARCHAEOLOGY and a comment posted a second ago sits on a page nobody asked for. The author saw
   * an empty-looking result, assumed it was lost, and posted it again — real duplicates in the
   * database, caused entirely by the UI never turning the page.
   */
  const loadThread = useCallback(async (wantedId?: string) => {
    setBusyThread(true);
    try {
      let collected: MemeComment[] = [];
      let page = 0;
      let full = false;
      do {
        const chunk = await listComments(memeId, token, page);
        collected = [...collected, ...chunk];
        full = chunk.length === COMMENTS_PAGE_SIZE;
        page += 1;
      } while (full && wantedId !== undefined
               && !collected.some((c) => c.id === wantedId) && page < MAX_THREAD_PAGES);
      setComments(collected);
      setPagesLoaded(page);
      setMoreComments(full);
      setThreadError(false);
      return collected;
    } catch {
      // the thread is unreadable — say so, and let the meme, its tags and its votes stand.
      // This catch is why a broken comment database no longer takes the gallery down with it.
      setThreadError(true);
      return null;
    } finally {
      setBusyThread(false);
    }
  }, [memeId, token]);

  /** The next page: newer comments, since the server hands them out oldest first. */
  const loadNewerComments = async () => {
    if (busyThread) return;
    setBusyThread(true);
    try {
      const chunk = await listComments(memeId, token, pagesLoaded);
      setComments((current) => [...current, ...chunk]);
      setPagesLoaded((n) => n + 1);
      setMoreComments(chunk.length === COMMENTS_PAGE_SIZE);
    } catch {
      setNotice('Could not load the rest of the thread — try again.');
    } finally {
      setBusyThread(false);
    }
  };

  const load = useCallback(() => {
    // four independent reads, four independent fates: one service having a bad day must cost only
    // its own part of this dialog
    void memeTally(memeId, token).then(setTally).catch(() => setTally({ score: null, myVote: null }));
    void loadThread();
    void memeTags(memeId).then(setTags).catch(() => setTags([]));
    void memeMeta(memeId, token)
      .then((m) => { setOwn(m.own === true); setNsfw(m.nsfw ?? false); })
      .catch(() => { setOwn(false); setNsfw(false); });
  }, [memeId, token, loadThread]);
  useEffect(load, [load]);

  const startEditing = () =>
    guard(() => { setTagDraft(tags.join(', ')); setTagError(null); setEditingTags(true); });

  const saveTags = () =>
    guard(async () => {
      const next = tagDraft.split(',').map((t) => t.trim()).filter(Boolean);
      const result = await setMemeTags(memeId, next, token);
      if (result.ok) {
        setTags(result.tags ?? next);
        setEditingTags(false);
        setTagError(null);
      } else if (result.status === 'NOT_THE_AUTHOR') {
        setTagError('Only the uploader can tag this meme.');
      } else if (result.status === 'INVALID_TAG') {
        setTagError('Tags are 2–30 chars: letters, digits and single dashes.');
      } else {
        setTagError(`Tagging refused (${result.status}).`);
      }
    });

  const guard = (action: () => void) => (token ? action() : onRequireSignIn());

  const removeMeme = () =>
    guard(async () => {
      if (!window.confirm('Delete this meme?')) return;
      if (await deleteMeme(memeId, token)) onDeleted();
      else window.alert('Could not delete this meme.');
    });

  const toggleNsfw = () =>
    guard(async () => {
      if (await setMemeNsfw(memeId, !nsfw, token)) setNsfw(!nsfw);
      else window.alert('Only a moderator may flag NSFW.');
    });

  const removeComment = (commentId: string) =>
    guard(async () => {
      const { ok, status } = await deleteComment(memeId, commentId, token);
      if (ok) {
        setComments((current) => current.filter((c) => c.id !== commentId));
      } else if (status !== 401) {
        // a 401 has already been through the refresh attempt inside `request`; if it is still 401
        // the session is gone and App has said so — anything else needs its own word here
        setNotice(`Could not delete that comment (${status}).`);
      }
    });

  const toggleHidden = (commentId: string, hidden: boolean) =>
    guard(async () => {
      if (await setCommentHidden(memeId, commentId, !hidden, token)) load();
      else window.alert('Only a moderator may hide a comment.');
    });

  const voteMeme = (direction: VoteDirection) =>
    guard(async () => {
      const { ok, tally: fresh, status } = await voteOnMeme(memeId, direction, token);
      if (ok && fresh) setTally(fresh);
      // a vote that did not count used to leave the arrow untouched and the user guessing; the
      // 401 branch is the whole point — the session dies after an hour and this is the click
      // people make most
      else if (status !== 401) setNotice(`Your vote did not go through (${status}).`);
      onVoted();
    });

  const voteComment = (commentId: string, direction: VoteDirection) =>
    guard(async () => {
      const { ok, tally: fresh, status } = await voteOnComment(memeId, commentId, direction, token);
      if (ok && fresh) {
        setComments((current) => current.map((c) =>
          c.id === commentId ? { ...c, score: fresh.score, myVote: fresh.myVote } : c));
      } else if (status !== 401) {
        setNotice(`Your vote did not go through (${status}).`);
      }
    });

  const submitComment = () =>
    guard(async () => {
      if (!text.trim() || busyThread) return;
      const result = await postComment(memeId, text, token);
      if (!result.ok) {
        // the draft STAYS in the field. It is the only copy, and the old code cleared it before
        // knowing whether the server had taken it
        if (result.status !== 401) {
          setNotice(result.detail === 'COMMENT_TOO_LONG' ? 'That comment is too long.'
            : result.detail === 'RATE_LIMITED' ? 'You are commenting too fast — wait a minute.'
            : `Your comment was not saved (${result.status}) — the text is still here, try again.`);
        }
        return;
      }
      setText('');
      setNotice(null);
      // walk the thread to wherever the server put it, so the author actually SEES their comment
      const thread = await loadThread(result.id);
      if (result.id !== undefined && thread !== null && !thread.some((c) => c.id === result.id)) {
        setNotice('Your comment was saved, but this thread is longer than this view — it is '
          + 'further down.');
      }
    });

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogContent>
        <Box component="img" src={`/memes/${memeId}`} alt="meme"
             sx={{ width: '100%', borderRadius: 2 }} />
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', my: 1 }}>
          <VoteButtons myVote={tally.myVote} onVote={voteMeme} />
          <Chip data-testid="meme-score" label={tally.score ?? 'n/a'} size="small" />
          {!token && <Typography variant="caption" color="text.secondary">sign in to vote or comment</Typography>}
          {nsfw && <Chip label="NSFW" size="small" color="warning" />}
          {isModerator && (
            <Button size="small" color="warning" sx={{ ml: 'auto' }} onClick={toggleNsfw}>
              {nsfw ? 'Unflag NSFW' : 'Flag NSFW'}
            </Button>
          )}
          {/* own, not author === user: /meta masks the uploader (a***@…), so only the server's
              own flag can say "yours"; === true keeps the button from strangers on an older
              memes API that does not send the field yet */}
          {(isModerator || own) && (
            <Button size="small" color="error" sx={{ ml: isModerator ? 0 : 'auto' }} onClick={removeMeme}>
              {own ? 'Delete' : 'Delete (moderator)'}
            </Button>
          )}
        </Stack>

        <Stack direction="row" spacing={0.5} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          {tags.map((t) => (
            <Chip key={t} label={`#${t}`} size="small" variant="outlined" onClick={() => onTagClick(t)} />
          ))}
          {!editingTags && (
            <Button size="small" onClick={startEditing}>{tags.length ? 'edit tags' : 'add tags'}</Button>
          )}
        </Stack>
        {editingTags && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start', mb: 1 }}>
            <TextField
              size="small" fullWidth autoFocus
              label="tags, comma-separated" placeholder="cats, monday-mood"
              value={tagDraft} onChange={(e) => setTagDraft(e.target.value)}
              error={tagError !== null} helperText={tagError ?? 'the uploader curates the whole set'}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); saveTags(); } }}
            />
            <Button variant="contained" onClick={saveTags}>Save</Button>
            <Button onClick={() => { setEditingTags(false); setTagError(null); }}>Cancel</Button>
          </Stack>
        )}

        <Divider />
        {/* the thread failed, the meme did not: the image, the votes and the tags above are all
            still here. This used to be the moment the entire React root came off. */}
        {threadError && (
          <Alert
            severity="warning"
            sx={{ mt: 1 }}
            action={<Button color="inherit" size="small" onClick={() => void loadThread()}>Retry</Button>}
          >
            Comments are unavailable right now — the meme itself is fine.
          </Alert>
        )}
        {comments.map((c) => (
          <Stack key={c.id} direction="row" spacing={1} sx={{ alignItems: 'center', py: 0.5 }}>
            <Typography variant="body2" sx={{ flex: 1 }}>
              <Box component="b" sx={{ color: 'primary.light' }}>{c.author}</Box>{' '}
              {c.hidden && c.text === null ? (
                <Box component="i" sx={{ color: 'text.disabled' }}>hidden by a moderator</Box>
              ) : (
                <Box component="span" sx={c.hidden ? { color: 'text.disabled', fontStyle: 'italic' } : undefined}>
                  {c.text}{c.hidden && ' (hidden by a moderator)'}
                </Box>
              )}
            </Typography>
            <Chip label={c.score ?? 'n/a'} size="small" variant="outlined" />
            <VoteButtons myVote={c.myVote} onVote={(d) => voteComment(c.id, d)} />
            {isModerator && (
              <IconButton size="small" aria-label={c.hidden ? 'reveal comment' : 'hide comment'}
                          title={c.hidden ? 'reveal (moderator)' : 'hide (moderator)'}
                          onClick={() => toggleHidden(c.id, c.hidden ?? false)}>
                {c.hidden ? <VisibilityIcon fontSize="inherit" /> : <VisibilityOffIcon fontSize="inherit" />}
              </IconButton>
            )}
            {/* c.own, not author === user: the listing masks authors (a***@…), so only the
                server's own flag can say "yours"; === true keeps the button from strangers
                on an older comments API that does not send the field yet */}
            {(isModerator || c.own === true) && (
              <IconButton size="small" aria-label="delete comment"
                          title={c.own === true ? 'delete your comment' : 'delete (moderator)'}
                          onClick={() => removeComment(c.id)}>
                <DeleteOutlineIcon fontSize="inherit" />
              </IconButton>
            )}
          </Stack>
        ))}
        {/* the thread is capped by the server and carries no total, so this is the honest wording:
            how much is on screen, and that there is more — never a silently truncated list */}
        {moreComments && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mt: 1 }}>
            <Typography variant="caption" color="text.secondary">
              Showing the oldest {comments.length} comments — there are more.
            </Typography>
            <Button size="small" onClick={() => void loadNewerComments()} disabled={busyThread}>
              Load newer
            </Button>
          </Stack>
        )}
        {notice && (
          <Alert severity="info" sx={{ mt: 1 }} onClose={() => setNotice(null)}>{notice}</Alert>
        )}
        <Stack component="form" direction="row" spacing={1} sx={{ mt: 1.5 }}
               onSubmit={(e) => { e.preventDefault(); submitComment(); }}>
          <TextField
            size="small" fullWidth
            placeholder={token ? 'add a comment…' : 'sign in to comment'}
            value={text} onChange={(e) => setText(e.target.value)}
          />
          <Button type="submit" variant="contained"
                  disabled={!token || !text.trim() || busyThread}>Post</Button>
        </Stack>
      </DialogContent>
    </Dialog>
  );
}
