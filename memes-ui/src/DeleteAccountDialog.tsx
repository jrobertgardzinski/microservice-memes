import { useState } from 'react';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { authHeader, jsonHeaders, SECURITY } from './api';

type Choice = 'default' | 'wipe' | 'popular';

interface Props {
  token: string;
  onDeleted: () => void;
  onClose: () => void;
}

/**
 * The deletion wizard: what should happen to the account's content is the leaver's choice, carried
 * with the request through the saga (the rule vocabulary belongs to the meme service).
 */
export default function DeleteAccountDialog({ token, onDeleted, onClose }: Props) {
  const [choice, setChoice] = useState<Choice>('default');
  const [minScore, setMinScore] = useState(100);
  const [busy, setBusy] = useState(false);
  // deleting is irreversible → step-up: confirm the password, then a factor code if one is enrolled
  const [password, setPassword] = useState('');
  const [stepUpTicket, setStepUpTicket] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);

  const doDelete = async () => {
    const rule = `KEEP_POPULAR_ANONYMIZED:${Math.max(1, minScore)}`;
    const purge =
      choice === 'wipe' ? { memes: 'DELETE', comments: 'DELETE' }
      : choice === 'popular' ? { memes: rule, comments: rule }
      : null;
    const r = await fetch(`${SECURITY}/account/delete`, {
      method: 'POST',
      headers: { ...jsonHeaders, ...authHeader(token) },
      body: JSON.stringify(purge ? { purge } : {}),
    });
    if (r.status === 202) onDeleted();
    else setError('Deletion was refused — please try again.');
  };

  // step 1: prove the password (and open the factor chain if the account has one)
  const submit = async () => {
    setBusy(true);
    setError(null);
    const r = await fetch(`${SECURITY}/account/step-up`, {
      method: 'POST',
      headers: { ...jsonHeaders, ...authHeader(token) },
      body: JSON.stringify({ action: 'delete-account', password }),
    });
    const body: { status?: string; stepUpTicket?: string } = await r.json().catch(() => ({}));
    if (r.status === 200 && body.status === 'ELEVATED') await doDelete();
    else if (r.status === 202 && body.status === 'FACTOR_REQUIRED') setStepUpTicket(body.stepUpTicket!);
    else setError('Wrong password.');
    setBusy(false);
  };

  // step 2 (only if a factor is enrolled): the mailed/authenticator code completes the step-up
  const submitCode = async () => {
    setBusy(true);
    setError(null);
    const r = await fetch(`${SECURITY}/account/step-up/factor`, {
      method: 'POST',
      headers: { ...jsonHeaders, ...authHeader(token) },
      body: JSON.stringify({ stepUpTicket, proof: code }),
    });
    if (r.status === 200) await doDelete();
    else setError('Wrong code.');
    setBusy(false);
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Delete your account</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          Your account locks immediately. What happens to what you posted is up to you:
        </Typography>
        <RadioGroup value={choice} onChange={(e) => setChoice(e.target.value as Choice)}>
          <FormControlLabel value="default" control={<Radio />}
            label={<span><b>Recommended:</b> delete my memes (with their comment threads); keep my
              comment texts elsewhere, signed “deleted account”</span>} />
          <FormControlLabel value="wipe" control={<Radio />}
            label="Burn it all: delete my memes and every comment I ever wrote" />
          <FormControlLabel value="popular" control={<Radio />}
            label="Keep what the community liked, anonymised — delete the rest" />
        </RadioGroup>
        {choice === 'popular' && (
          <TextField
            size="small" type="number" label="minimum votes to keep" sx={{ mt: 1 }}
            value={minScore}
            onChange={(e) => setMinScore(parseInt(e.target.value, 10) || 1)}
            inputProps={{ min: 1 }}
          />
        )}
        <Typography variant="body2" sx={{ mt: 2 }}>Confirm it is you:</Typography>
        {!stepUpTicket ? (
          <TextField size="small" type="password" label="your password" fullWidth sx={{ mt: 1 }}
            value={password} onChange={(e) => setPassword(e.target.value)} />
        ) : (
          <TextField size="small" label="sign-in code" fullWidth sx={{ mt: 1 }}
            value={code} onChange={(e) => setCode(e.target.value)} autoFocus />
        )}
        {error && <Typography variant="body2" color="error" sx={{ mt: 1 }}>{error}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Keep my account</Button>
        {!stepUpTicket ? (
          <Button color="error" variant="contained" disabled={busy || !password} onClick={() => void submit()}>
            Delete my account
          </Button>
        ) : (
          <Button color="error" variant="contained" disabled={busy || !code} onClick={() => void submitCode()}>
            Confirm & delete
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
