import { useCallback, useEffect, useState } from 'react';
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
import {
  authHeader, jsonHeaders, listComments, memeTally, MemeComment, VoteDirection, VoteTally,
} from './api';

interface Props {
  memeId: string;
  token: string | null;
  onVoted: () => void;
  onRequireSignIn: () => void;
  onClose: () => void;
}

/** Arrows with the caller's current vote pressed; clicking the pressed one retracts (the API toggles). */
function VoteButtons({ myVote, onVote }: { myVote: VoteDirection | null; onVote: (d: VoteDirection) => void }) {
  return (
    <>
      <IconButton size="small" onClick={() => onVote('UP')}
                  color={myVote === 'UP' ? 'primary' : 'default'}
                  sx={myVote === 'UP' ? { bgcolor: 'primary.dark' } : undefined}>
        <ArrowUpwardIcon fontSize="inherit" />
      </IconButton>
      <IconButton size="small" onClick={() => onVote('DOWN')}
                  color={myVote === 'DOWN' ? 'error' : 'default'}
                  sx={myVote === 'DOWN' ? { bgcolor: 'error.dark' } : undefined}>
        <ArrowDownwardIcon fontSize="inherit" />
      </IconButton>
    </>
  );
}

export default function MemeDialog({ memeId, token, onVoted, onRequireSignIn, onClose }: Props) {
  const [tally, setTally] = useState<VoteTally>({ score: 0, myVote: null });
  const [comments, setComments] = useState<MemeComment[]>([]);
  const [text, setText] = useState('');

  const load = useCallback(() => {
    void memeTally(memeId, token).then(setTally);
    void listComments(memeId, token).then(setComments);
  }, [memeId, token]);
  useEffect(load, [load]);

  const guard = (action: () => void) => (token ? action() : onRequireSignIn());

  const voteMeme = (direction: VoteDirection) =>
    guard(async () => {
      const r = await fetch(`/memes/${memeId}/votes`, {
        method: 'POST',
        headers: { ...jsonHeaders, ...authHeader(token) },
        body: JSON.stringify({ direction }),
      });
      if (r.ok) setTally(await r.json());
      onVoted();
    });

  const voteComment = (commentId: string, direction: VoteDirection) =>
    guard(async () => {
      const r = await fetch(`/memes/${memeId}/comments/${commentId}/votes`, {
        method: 'POST',
        headers: { ...jsonHeaders, ...authHeader(token) },
        body: JSON.stringify({ direction }),
      });
      if (r.ok) {
        const updated: VoteTally = await r.json();
        setComments((current) => current.map((c) =>
          c.id === commentId ? { ...c, score: updated.score, myVote: updated.myVote } : c));
      }
    });

  const postComment = () =>
    guard(async () => {
      if (!text.trim()) return;
      const r = await fetch(`/memes/${memeId}/comments`, {
        method: 'POST',
        headers: { ...jsonHeaders, ...authHeader(token) },
        body: JSON.stringify({ text }),
      });
      if (r.ok) {
        setText('');
        load();
      }
    });

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogContent>
        <Box component="img" src={`/memes/${memeId}`} alt="meme"
             sx={{ width: '100%', borderRadius: 2 }} />
        <Stack direction="row" spacing={1} alignItems="center" sx={{ my: 1 }}>
          <VoteButtons myVote={tally.myVote} onVote={voteMeme} />
          <Chip label={tally.score} size="small" />
          {!token && <Typography variant="caption" color="text.secondary">sign in to vote or comment</Typography>}
        </Stack>
        <Divider />
        {comments.map((c) => (
          <Stack key={c.id} direction="row" spacing={1} alignItems="center" sx={{ py: 0.5 }}>
            <Typography variant="body2" sx={{ flex: 1 }}>
              <Box component="b" sx={{ color: 'primary.light' }}>{c.author}</Box> {c.text}
            </Typography>
            <Chip label={c.score} size="small" variant="outlined" />
            <VoteButtons myVote={c.myVote} onVote={(d) => voteComment(c.id, d)} />
          </Stack>
        ))}
        <Stack component="form" direction="row" spacing={1} sx={{ mt: 1.5 }}
               onSubmit={(e) => { e.preventDefault(); postComment(); }}>
          <TextField
            size="small" fullWidth
            placeholder={token ? 'add a comment…' : 'sign in to comment'}
            value={text} onChange={(e) => setText(e.target.value)}
          />
          <Button type="submit" variant="contained" disabled={!token || !text.trim()}>Post</Button>
        </Stack>
      </DialogContent>
    </Dialog>
  );
}
