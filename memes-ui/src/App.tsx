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
import IconButton from '@mui/material/IconButton';
import Snackbar from '@mui/material/Snackbar';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import UploadIcon from '@mui/icons-material/Upload';
import SettingsIcon from '@mui/icons-material/Settings';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import AdminPanel from './AdminPanel';
import AuthPanel from './AuthPanel';
import MemeDialog from './MemeDialog';
import {
  authHeader, GALLERY_PAGE_SIZE, listFavourites, listMemes, MemeRef, memeScores, removeFavourite,
  saveFavourite, SECURITY,
} from './api';

export default function App() {
  const [token, setToken] = useState<string | null>(localStorage.getItem('accessToken'));
  const [user, setUser] = useState('');
  const [isModerator, setIsModerator] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [adminOpen, setAdminOpen] = useState(false);
  const [memes, setMemes] = useState<MemeRef[]>([]);
  // the wall is paged (the server caps how much of the gallery one call may return), so the
  // visitor asks for the next page instead of the wall silently ending at the first one
  const [pagesShown, setPagesShown] = useState(1);
  const [moreToShow, setMoreToShow] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  // scores of the tiles on screen, keyed by meme id. A Map and not a Record on purpose: `get`
  // answers `undefined` for a meme whose score this UI does not know, and an unknown score must
  // stay visibly unknown — see ScoreChip. Nothing here may substitute a zero.
  const [scores, setScores] = useState<ReadonlyMap<string, number>>(new Map());
  const [selected, setSelected] = useState<string | null>(null);
  const [warning, setWarning] = useState<string | null>(null);
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  // favourites live in microservice-user-collections: opaque meme refs this gallery hydrates.
  // null = signed out or the service is unreachable — the feature degrades, the wall never breaks
  const [favourites, setFavourites] = useState<string[] | null>(null);
  const [showFavourites, setShowFavourites] = useState(false);

  const refresh = useCallback(() => {
    void listMemes(tagFilter ?? undefined, 0).then((page) => {
      setMemes(page);
      setPagesShown(1);
      // a full page is the only hint that more may follow — the listing carries no total
      setMoreToShow(page.length === GALLERY_PAGE_SIZE);
    });
  }, [tagFilter]);
  useEffect(refresh, [refresh]);

  // The numbers under the tiles are asked for BY ID, for the tiles actually on screen. They used to
  // come out of /memes/hot, which is a ranking of the hottest hundred — every meme below the cap was
  // missing from it, and the missing entry rendered as "▲ 0" under a meme that had votes.
  //
  // Merged, never replaced: the wall and the favourites view are two sets of tiles, and learning one
  // set's scores must not turn the other's back into "unknown". A fresh answer overwrites the older
  // value for the same id, so a vote still moves the number — and re-asking about tiles we already
  // have a number for is the point, not waste: one query per fifty tiles buys a wall that is
  // current after every vote and every "Load more".
  useEffect(() => {
    const onScreen = showFavourites ? (favourites ?? []) : memes.map((m) => m.id);
    void memeScores(onScreen).then((fresh) =>
      setScores((known) => new Map([...known, ...fresh])));
  }, [memes, favourites, showFavourites]);

  const showMore = () => {
    // the guard is not cosmetic: two clicks before the first answer lands would append the same
    // page twice, and duplicate tiles mean duplicate React keys
    if (loadingMore) return;
    setLoadingMore(true);
    void listMemes(tagFilter ?? undefined, pagesShown)
      .then((page) => {
        setMemes((current) => [...current, ...page]);
        setPagesShown((shown) => shown + 1);
        setMoreToShow(page.length === GALLERY_PAGE_SIZE);
      })
      .finally(() => setLoadingMore(false));
  };

  const loadFavourites = useCallback((t: string) => {
    void listFavourites(t)
      .then((refs) => setFavourites(refs.filter((r) => r.itemType === 'meme').map((r) => r.itemId)))
      .catch(() => setFavourites(null));
  }, []);

  useEffect(() => {
    if (!token) {
      setUser('');
      setIsModerator(false);
      setIsAdmin(false);
      setFavourites(null);
      setShowFavourites(false);
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
        setIsAdmin(roles.includes('ADMIN'));
      })
      .catch(() => setToken(null));
    loadFavourites(token);
  }, [token, loadFavourites]);

  const requireSignIn = () => setWarning('Sign in first — browsing is public, contributing is not.');

  const toggleFavourite = async (memeId: string) => {
    if (!token || favourites === null) return;
    const isFavourite = favourites.includes(memeId);
    // optimistic — the star answers instantly; a failed call rolls back with a notice
    setFavourites(isFavourite ? favourites.filter((id) => id !== memeId) : [memeId, ...favourites]);
    const ok = isFavourite ? await removeFavourite(memeId, token) : await saveFavourite(memeId, token);
    if (!ok) {
      setFavourites(favourites);
      setWarning('The favourites service did not answer — try again.');
    }
  };

  const upload = async (file: File) => {
    const body = new FormData();
    body.append('file', file);
    const r = await fetch('/memes', { method: 'POST', headers: authHeader(token), body });
    if (r.status !== 201) setWarning(`Upload refused (${r.status}).`);
    refresh();
  };

  const star = (memeId: string) => (
    <IconButton
      size="small"
      aria-label={favourites?.includes(memeId) ? 'unfavourite' : 'favourite'}
      onClick={() => void toggleFavourite(memeId)}
      sx={{ position: 'absolute', top: 2, left: 2, zIndex: 1,
            color: favourites?.includes(memeId) ? 'warning.main' : 'rgba(255,255,255,0.8)' }}
    >
      {favourites?.includes(memeId) ? <StarIcon /> : <StarBorderIcon />}
    </IconButton>
  );

  return (
    <>
      <AppBar position="sticky" color="default">
        <Toolbar variant="dense">
          <Typography variant="h6" sx={{ flex: 1 }}>memes</Typography>
          {token && favourites !== null && (
            <Button
              startIcon={showFavourites ? <StarIcon /> : <StarBorderIcon />}
              color={showFavourites ? 'warning' : 'inherit'}
              onClick={() => {
                if (!showFavourites && token) loadFavourites(token);
                setShowFavourites(!showFavourites);
              }}
              sx={{ mr: 1 }}
            >
              Favourites
            </Button>
          )}
          {isAdmin && (
            <Button startIcon={<SettingsIcon />} onClick={() => setAdminOpen(true)} sx={{ mr: 1 }}>
              Admin
            </Button>
          )}
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

          {tagFilter && !showFavourites && (
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary">Filtered by tag:</Typography>
              <Chip label={`#${tagFilter}`} color="primary" onDelete={() => setTagFilter(null)} />
            </Stack>
          )}

          {showFavourites ? (
            <>
              <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))' }}>
                {(favourites ?? []).map((id) => (
                  <FavouriteTile key={id} memeId={id} nsfw={memes.find((m) => m.id === id)?.nsfw}
                                 score={scores.get(id)} star={star(id)} onOpen={() => setSelected(id)} />
                ))}
              </Box>
              {(favourites ?? []).length === 0 && (
                <Typography color="text.secondary">
                  No favourites yet — star a meme on the wall and it lands here.
                </Typography>
              )}
            </>
          ) : (
            <>
              <Box sx={{ display: 'grid', gap: 1.5, gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))' }}>
                {memes.map((m) => (
                  <Card key={m.id} sx={{ position: 'relative' }}>
                    <CardActionArea onClick={() => setSelected(m.id)}>
                      <CardMedia component="img" image={`/memes/${m.id}/thumbnail`} loading="lazy"
                                 sx={{ aspectRatio: '1', objectFit: 'cover',
                                       // NSFW: the moderators' flag blurs the tile; opening the
                                       // dialog is the deliberate act that reveals the image
                                       ...(m.nsfw ? { filter: 'blur(14px)' } : {}) }} />
                      {m.nsfw && (
                        <Chip label="NSFW" size="small" color="warning"
                              sx={{ position: 'absolute', top: 6, right: 6 }} />
                      )}
                      <ScoreChip score={scores.get(m.id)} />
                    </CardActionArea>
                    {token && favourites !== null && star(m.id)}
                  </Card>
                ))}
              </Box>
              {moreToShow && (
                <Button onClick={showMore} disabled={loadingMore} sx={{ alignSelf: 'center' }}>
                  Load more
                </Button>
              )}
              {memes.length === 0 && (
                <Typography color="text.secondary">
                  {tagFilter ? `No memes tagged #${tagFilter}.` : 'No memes yet — sign in and upload the first one.'}
                </Typography>
              )}
            </>
          )}
        </Stack>
      </Container>

      {selected && (
        <MemeDialog memeId={selected} token={token} isModerator={isModerator}
                    onVoted={refresh} onRequireSignIn={requireSignIn}
                    onTagClick={(t) => { setTagFilter(t); setSelected(null); }}
                    onDeleted={() => { setSelected(null); refresh(); }}
                    onClose={() => setSelected(null)} />
      )}

      <AdminPanel token={token} open={adminOpen} onClose={() => setAdminOpen(false)} />

      <Snackbar open={warning !== null} autoHideDuration={4000} onClose={() => setWarning(null)}>
        <Alert severity="warning" onClose={() => setWarning(null)}>{warning}</Alert>
      </Snackbar>
    </>
  );
}

/**
 * The vote count on a tile — and the one place that decides what "no number" looks like.
 *
 * `undefined` is NOT zero. It means this UI has no tally for the meme: the gallery did not answer
 * for that id (a favourite whose meme is gone), or the score call failed. Then the chip says "n/a",
 * the same word the dialog uses for a null tally — because "▲ 0" under a meme is a statement, and
 * "nobody voted for this" is a statement we would be making up. A meme that really has no votes
 * comes back from the server as a 0 and gets the confident "▲ 0" it has earned.
 */
function ScoreChip({ score }: { score: number | undefined }) {
  const known = score !== undefined;
  return (
    <Chip
      data-testid="tile-score"
      label={known ? `▲ ${score}` : '▲ n/a'}
      aria-label={known ? `score ${score}` : 'score unknown'}
      size="small"
      sx={{ position: 'absolute', bottom: 6, left: 6, ...(known ? {} : { opacity: 0.6 }) }}
    />
  );
}

/**
 * A tile on the favourites wall: a hydrated ref. The ref outlives its meme by design — the
 * collections service stores opaque ids and never checks back — so a thumbnail that no longer
 * loads renders as an "unavailable" keepsake, with the star still there to let it go.
 */
function FavouriteTile({ memeId, nsfw, score, star, onOpen }: {
  memeId: string;
  nsfw: boolean | undefined;
  score: number | undefined;
  star: JSX.Element;
  onOpen: () => void;
}) {
  const [gone, setGone] = useState(false);
  return (
    <Card sx={{ position: 'relative' }}>
      {gone ? (
        <Box sx={{ aspectRatio: '1', display: 'flex', alignItems: 'center', justifyContent: 'center',
                   bgcolor: 'action.hover' }}>
          <Typography variant="body2" color="text.secondary">unavailable</Typography>
        </Box>
      ) : (
        <CardActionArea onClick={onOpen}>
          {/* the wall's thumbnail may sit in the browser cache long after the meme is gone —
              a distinct URL forces a real answer, and a 404 turns the tile into a keepsake */}
          <CardMedia component="img" image={`/memes/${memeId}/thumbnail?wall=favourites`} loading="lazy"
                     onError={() => setGone(true)}
                     sx={{ aspectRatio: '1', objectFit: 'cover',
                           ...(nsfw ? { filter: 'blur(14px)' } : {}) }} />
          {nsfw && (
            <Chip label="NSFW" size="small" color="warning"
                  sx={{ position: 'absolute', top: 6, right: 6 }} />
          )}
          <ScoreChip score={score} />
        </CardActionArea>
      )}
      {star}
    </Card>
  );
}
