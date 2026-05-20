import { Bell, CalendarCheck, Eye, EyeOff, Users } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api, { unwrapData } from '../api/axios';
import Button from '../components/shared/Button';
import Input from '../components/shared/Input';
import { useAuth } from '../hooks/useAuth';

const BrandPanel = () => (
  <aside className="auth-brand login-branding">
    <div className="logo-mark" style={{ background: 'rgba(255,255,255,0.18)', marginBottom: 34 }}>
      <CalendarCheck size={24} />
    </div>
    <h1>Meet Smarter.</h1>
    <p>Schedule meetings around real availability, not just hope.</p>
    <div style={{ marginTop: 38, display: 'grid', gap: 18 }}>
      {[
        [CalendarCheck, 'Automatic conflict detection'],
        [Users, 'Real-time collaborative scheduling'],
        [Bell, 'Instant notifications'],
      ].map(([Icon, text]) => (
        <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 12, fontWeight: 700 }}>
          <span style={{ width: 38, height: 38, borderRadius: 12, display: 'grid', placeItems: 'center', background: 'rgba(255,255,255,0.14)' }}><Icon size={19} /></span>
          {text}
        </div>
      ))}
    </div>
  </aside>
);

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const auth = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    setError('');
    try {
      const loginResponse = await api.post('/api/auth/login', { email, password });
      const authData = unwrapData(loginResponse.data);
      const profileResponse = await api.get('/api/users/me', {
        headers: { Authorization: `Bearer ${authData.accessToken}` },
      });
      auth.login(authData, unwrapData(profileResponse.data));
      navigate('/calendar');
    } catch (err) {
      if (!err.response) setError('Server unavailable. Please start the backend and try again.');
      else if (err.response.status === 401) setError('Invalid email or password.');
      else setError(err.response.data?.message || 'Unable to sign in right now.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="auth-page login-split">
      <BrandPanel />
      <section className="auth-form-wrap">
        <form className="card auth-card modal-enter" onSubmit={handleSubmit}>
          <div className="logo-mark" style={{ marginBottom: 20 }}>
            <CalendarCheck size={22} />
          </div>
          <h2 style={{ fontSize: 30, marginBottom: 8 }}>Welcome back</h2>
          <p className="muted" style={{ marginTop: 0, marginBottom: 26 }}>Sign in to your account</p>

          {error ? <div className="alert alert-danger" style={{ marginBottom: 16 }}>{error}</div> : null}

          <div style={{ display: 'grid', gap: 16 }}>
            <Input label="Email" type="email" placeholder="you@company.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <div style={{ position: 'relative' }}>
              <Input label="Password" type={showPassword ? 'text' : 'password'} placeholder="Enter your password" value={password} onChange={(e) => setPassword(e.target.value)} required />
              <button type="button" className="btn-ghost" onClick={() => setShowPassword((value) => !value)} style={{ position: 'absolute', right: 8, top: 34, padding: 8 }}>
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            <Button type="submit" loading={isLoading}>Sign In</Button>
          </div>

          <p style={{ textAlign: 'center', marginTop: 22, color: 'var(--color-text-secondary)' }}>
            Don&apos;t have an account? <Link to="/register" style={{ color: 'var(--color-primary)', fontWeight: 800 }}>Create one</Link>
          </p>
        </form>
      </section>
    </main>
  );
}
