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
import { authHeader, jsonHeaders, request, SECURITY } from './api';

type Choice = 'default' | 'wipe' | 'popular';

/**
 * What to tell the person, per status. "Wrong password." used to cover EVERY non-2xx here — a
 * throttled attempt, a 500, a service restart — so somebody whose password was right retyped it
 * until they gave up, which is the same defect security-ui already fixed on its side (P18 poz. 40).
 * It matters more since step-up gained a rate limit: 429 is now a routine answer, and it means
 * "wait", not "you typed it wrong".
 */
const messageFor = (status: number, wrong: string): string => {
  if (status === 401 || status === 403) return wrong;
  if (status === 429) return 'Too many attempts — wait a moment and try again.';
  return `Security answered ${status}. Please try again.`;
};

/** A request that never arrived says nothing about the password — and must not wedge the dialog. */
const UNREACHABLE = 'Could not reach the security service — check your connection and try again.';

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
    // Every option ships EXPLICIT rules, the preselected one included. It used to send `{}`, and an
    // empty map is not "the wizard's default" on the wire — identity's PurgeChoices calls it
    // "whatever each content service's deployment default is", so the orchestrator omits the policy
    // field and the admin's runtime override decides instead. The radio button says "delete my
    // memes", three javadocs promise the leaver's wish outranks the override, and neither was true
    // while the wish travelled as silence (P18 poz. 18). The pair below IS the label, spelled in the
    // content services' vocabulary; it also happens to be their env defaults, so nothing changes
    // for a deployment without an override — only the override stops overruling a stated choice.
    const purge =
      choice === 'wipe' ? { memes: 'DELETE', comments: 'DELETE' }
      : choice === 'popular' ? { memes: rule, comments: rule }
      : { memes: 'DELETE', comments: 'ANONYMIZE_AUTHOR' };
    const r = await request(`${SECURITY}/account/delete`, {
      method: 'POST',
      headers: { ...jsonHeaders, ...authHeader(token) },
      body: JSON.stringify({ purge }),
    });
    if (r.status === 202) onDeleted();
    else setError(messageFor(r.status, 'Deletion was refused — please try again.'));
  };

  // step 1: prove the password (and open the factor chain if the account has one)
  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await request(`${SECURITY}/account/step-up`, {
        method: 'POST',
        headers: { ...jsonHeaders, ...authHeader(token) },
        body: JSON.stringify({ action: 'delete-account', password }),
      });
      const body: { status?: string; stepUpTicket?: string } = await r.json().catch(() => ({}));
      if (r.status === 200 && body.status === 'ELEVATED') await doDelete();
      else if (r.status === 202 && body.status === 'FACTOR_REQUIRED') setStepUpTicket(body.stepUpTicket!);
      else setError(messageFor(r.status, 'Wrong password.'));
    } catch {
      setError(UNREACHABLE);
    } finally {
      // in a finally, because a throw used to skip it and leave every button disabled for good:
      // the only way out of the wizard was reloading the page
      setBusy(false);
    }
  };

  // step 2 (only if a factor is enrolled): the mailed/authenticator code completes the step-up
  const submitCode = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await request(`${SECURITY}/account/step-up/factor`, {
        method: 'POST',
        headers: { ...jsonHeaders, ...authHeader(token) },
        body: JSON.stringify({ stepUpTicket, proof: code }),
      });
      if (r.status === 200) await doDelete();
      else setError(messageFor(r.status, 'Wrong code.'));
    } catch {
      setError(UNREACHABLE);
    } finally {
      setBusy(false);
    }
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
            slotProps={{ htmlInput: { min: 1 } }}
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
