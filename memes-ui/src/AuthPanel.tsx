import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import MarkEmailReadIcon from '@mui/icons-material/MarkEmailRead';
import { jsonHeaders, Notice, SECURITY } from './api';

type Mode = 'signin' | 'signup' | 'inbox';

const prettify = (code: string) => code.toLowerCase().replaceAll('_', ' ');

interface Props {
  token: string | null;
  user: string;
  onToken: (token: string) => void;
  onLogout: () => void;
}

export default function AuthPanel({ token, user, onToken, onLogout }: Props) {
  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);

  // the verification mail links here (?verify=<token>); confirm it on arrival
  useEffect(() => {
    const mailed = new URLSearchParams(location.search).get('verify');
    if (!mailed) return;
    history.replaceState(null, '', location.pathname);
    void fetch(`${SECURITY}/verify-email`, {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify({ token: mailed }),
    }).then((r) =>
      setNotice(
        r.ok
          ? { tone: 'success', text: 'E-mail verified — sign in below.' }
          : { tone: 'warning', text: 'This verification link was already used or replaced by a newer one.' },
      ),
    );
  }, []);

  if (token) {
    return (
      <Chip
        label={`signed in as ${user || '…'}`}
        color="primary"
        variant="outlined"
        onDelete={onLogout}
        deleteIcon={<span style={{ fontSize: '0.8rem', padding: '0 6px' }}>sign out</span>}
      />
    );
  }

  const signUp = async () => {
    const r = await fetch(`${SECURITY}/register`, {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify({ email, password }),
    });
    if (r.status === 201) {
      setMode('inbox');
    } else if (r.status === 409) {
      setMode('signin');
      setNotice({ tone: 'warning', text: 'This e-mail is already registered — sign in instead.' });
    } else if (r.status === 422) {
      const errors: { emailErrors?: string[]; passwordErrors?: string[] } = await r.json();
      setNotice({
        tone: 'warning',
        text: 'That will not do:',
        items: [...(errors.emailErrors ?? []), ...(errors.passwordErrors ?? [])].map(prettify),
      });
    } else {
      setNotice({ tone: 'warning', text: `Registration failed (${r.status}).` });
    }
  };

  const signIn = async () => {
    const r = await fetch(`${SECURITY}/authenticate`, {
      method: 'POST',
      headers: jsonHeaders,
      body: JSON.stringify({ email, password }),
    });
    if (r.ok) {
      const body: { accessToken: string } = await r.json();
      onToken(body.accessToken);
    } else if (r.status === 403) {
      setNotice({ tone: 'warning', text: 'E-mail not verified yet — click the link in the mail (inbox: Mailpit, localhost:8025).' });
    } else if (r.status === 429) {
      setNotice({ tone: 'warning', text: 'Too many failed attempts from this machine — blocked for a few minutes.' });
    } else {
      setNotice({ tone: 'warning', text: 'Wrong e-mail or password.' });
    }
  };

  const submit = async () => {
    setBusy(true);
    setNotice(null);
    try {
      await (mode === 'signup' ? signUp() : signIn());
    } finally {
      setBusy(false);
    }
  };

  if (mode === 'inbox') {
    return (
      <Paper sx={{ p: 2, maxWidth: 430 }}>
        <Stack spacing={1.5} alignItems="flex-start">
          <Stack direction="row" spacing={1} alignItems="center">
            <MarkEmailReadIcon color="primary" />
            <Typography>
              Almost there — we mailed a verification link to <b>{email}</b>.
            </Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            Open the inbox (<Link href="http://localhost:8025" target="_blank">Mailpit</Link>),
            click the link, and it brings you back here ready to sign in.
          </Typography>
          <Button variant="outlined" onClick={() => setMode('signin')}>
            I clicked it — sign in
          </Button>
        </Stack>
      </Paper>
    );
  }

  return (
    <Paper sx={{ p: 2, maxWidth: 430 }}>
      <Tabs value={mode} onChange={(_, m: Mode) => { setMode(m); setNotice(null); }} sx={{ mb: 1.5 }}>
        <Tab label="Sign in" value="signin" />
        <Tab label="Create account" value="signup" />
      </Tabs>
      <Box component="form" onSubmit={(e) => { e.preventDefault(); void submit(); }}>
        <Stack spacing={1.5}>
          <TextField
            label="e-mail" type="email" size="small" autoComplete="email"
            value={email} onChange={(e) => setEmail(e.target.value)}
          />
          <TextField
            label="password" type="password" size="small"
            autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
            value={password} onChange={(e) => setPassword(e.target.value)}
          />
          <Button type="submit" variant="contained" disabled={busy || !email || !password}>
            {mode === 'signup' ? 'Create account' : 'Sign in'}
          </Button>
        </Stack>
      </Box>
      {notice && (
        <Alert severity={notice.tone} sx={{ mt: 1.5 }}>
          {notice.text}
          {notice.items && (
            <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
              {notice.items.map((item) => <li key={item}>{item}</li>)}
            </ul>
          )}
        </Alert>
      )}
    </Paper>
  );
}
