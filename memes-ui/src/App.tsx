import { useCallback, useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardMedia from '@mui/material/CardMedia';
import Chip from '@mui/material/Chip';
import Container from '@mui/material/Container';
import Snackbar from '@mui/material/Snackbar';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import UploadIcon from '@mui/icons-material/Upload';
import AuthPanel from './AuthPanel';
import MemeDialog from './MemeDialog';
import { authHeader, hotMemes, listMemes, MemeRef, SECURITY } from './api';

export default function App() {
  const [token, setToken] = useState<string | null>(localStorage.getItem('accessToken'));
  const [user, setUser] = useState('');
  const [isModerator, setIsModerator] = useState(false);
  const [memes, setMemes] = useState<MemeRef[]>([]);
  const [scores, setScores] = useState<Record<string, number>>({});
  const [selected, setSelected] = useState<string | null>(null);
  const [warning, setWarning] = useState<string | null>(null);
  const [tagFilter, setTagFilter] = useState<string | null>(null);

  const refresh = useCallback(() => {
    void listMemes(tagFilter ?? undefined).then(setMemes);
    void hotMemes().then((hot) =>
      setScores(Object.fromEntries(hot.map((h) => [h.memeId, h.score]))),
    );
  }, [tagFilter]);
  useEffect(refresh, [refresh]);

  useEffect(() => {
    if (!token) {
      setUser('');
      setIsModerator(false);
      localStorage.removeItem('accessToken');
      return;
    }
    localStorage.setItem('accessToken', token);
    void fetch(`${SECURITY}/me`, { headers: authHeader(token) })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error('expired'))))
      .then((me: { email: string; roles?: string[] }) => {
        setUser(me.email);
        const roles = me.roles ?? [];
        setIsModerator(roles.includes('MODERATOR') || roles.includes('ADMIN'));
      })
      .catch(() => setToken(null));
  }, [token]);

  const requireSignIn = () => setWarning('Sign in first — browsing is public, contributing is not.');

  const upload = async (file: File) => {
    const body = new FormData();
    body.append('file', file);
    const r = await fetch('/memes', { method: 'POST', headers: authHeader(token), body });
    if (r.status !== 201) setWarning(`Upload refused (${r.status}).`);
    refresh();
  };

  return (
    <>
      <AppBar position="sticky" color="default">
        <Toolbar variant="dense">
          <Typography variant="h6" sx={{ flex: 1 }}>memes</Typography>
          <Button component="label" variant="contained" startIcon={<UploadIcon />}
                  onClick={(e) => { if (!token) { e.preventDefault(); requireSignIn(); } }}>
            Upload
            <input type="file" accept="image/*" hidden
                   onChange={(e) => {
                     const file = e.target.files?.[0];
                     if (file) void upload(file);
                     e.target.value = '';
                   }} />
          </Button>
        </Toolbar>
      </AppBar>

      <Container maxWidth="md" sx={{ py: 2 }}>
        <Stack spacing={2}>
          <AuthPanel token={token} user={user} onToken={setToken} onLogout={() => setToken(null)} />

          {tagFilter && (
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary">Filtered by tag:</Typography>
              <Chip label={`#${tagFilter}`} color="primary" onDelete={() => setTagFilter(null)} />
            </Stack>
          )}

          <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))' }}>
            {memes.map((m) => (
              <Card key={m.id}>
                <CardActionArea onClick={() => setSelected(m.id)}>
                  <CardMedia component="img" image={`/memes/${m.id}/thumbnail`} loading="lazy"
                             sx={{ aspectRatio: '1', objectFit: 'cover' }} />
                  <Chip label={`▲ ${scores[m.id] ?? 0}`} size="small"
                        sx={{ position: 'absolute', bottom: 6, left: 6 }} />
                </CardActionArea>
              </Card>
            ))}
          </Box>
          {memes.length === 0 && (
            <Typography color="text.secondary">
              {tagFilter ? `No memes tagged #${tagFilter}.` : 'No memes yet — sign in and upload the first one.'}
            </Typography>
          )}
        </Stack>
      </Container>

      {selected && (
        <MemeDialog memeId={selected} token={token} user={user} isModerator={isModerator}
                    onVoted={refresh} onRequireSignIn={requireSignIn}
                    onTagClick={(t) => { setTagFilter(t); setSelected(null); }}
                    onDeleted={() => { setSelected(null); refresh(); }}
                    onClose={() => setSelected(null)} />
      )}

      <Snackbar open={warning !== null} autoHideDuration={4000} onClose={() => setWarning(null)}>
        <Alert severity="warning" onClose={() => setWarning(null)}>{warning}</Alert>
      </Snackbar>
    </>
  );
}
