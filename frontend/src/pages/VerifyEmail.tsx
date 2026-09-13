import { FormEvent, useState } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import api from '../api/client';

export default function VerifyEmail() {
  const location = useLocation();
  const navigate = useNavigate();
  const [email, setEmail] = useState((location.state as any)?.email || '');
  const [code, setCode] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function handleVerify(e: FormEvent) {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      const res = await api.post('/api/auth/verify', { email, code });
      setMessage(res.data.message + ' Redirecting to login…');
      setTimeout(() => navigate('/login'), 1500);
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Verification failed');
    }
  }

  async function handleResend() {
    setError('');
    setMessage('');
    try {
      const res = await api.post('/api/auth/resend-verification', { email });
      setMessage(res.data.message);
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Could not resend code');
    }
  }

  return (
    <div className="auth-page">
      <form className="card" onSubmit={handleVerify}>
        <h1>Verify your email</h1>
        <p className="muted small">
          In local development, the verification code is printed to the backend's
          console/logs instead of a real email being sent.
        </p>
        <label>Email</label>
        <input value={email} onChange={(e) => setEmail(e.target.value)} required />
        <label>Verification code</label>
        <input value={code} onChange={(e) => setCode(e.target.value)} required maxLength={6} />
        {message && <p className="hint">{message}</p>}
        {error && <p className="error">{error}</p>}
        <button type="submit">Verify</button>
        <p>
          <button type="button" onClick={handleResend} style={{ background: 'transparent', color: 'var(--primary)', padding: 0 }}>
            Resend code
          </button>
        </p>
        <p><Link to="/login">Back to login</Link></p>
      </form>
    </div>
  );
}
