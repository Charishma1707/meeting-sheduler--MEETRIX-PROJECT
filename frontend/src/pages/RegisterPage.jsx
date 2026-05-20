import { Bell, CalendarCheck, Eye, EyeOff, Users } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api, { setTokens, unwrapData } from '../api/axios';
import Button from '../components/shared/Button';
import Input from '../components/shared/Input';
import TimezoneSelect from '../components/shared/TimezoneSelect';
import { useAuth } from '../hooks/useAuth';

const preferences = [
  { label: 'Email Only', value: 'EMAIL' },
  { label: 'In-App Only', value: 'IN_APP' },
  { label: 'Both (Recommended)', value: 'BOTH' },
];

const BrandPanel = () => (
  <aside className="auth-brand login-branding">
    <div className="logo-mark" style={{ background: 'rgba(255,255,255,0.18)', marginBottom: 34 }}><CalendarCheck size={24} /></div>
    <h1>Meet Smarter.</h1>
    <p>Schedule meetings around real availability, not just hope.</p>
    <div style={{ marginTop: 38, display: 'grid', gap: 18 }}>
      {[[CalendarCheck, 'Automatic conflict detection'], [Users, 'Real-time collaborative scheduling'], [Bell, 'Instant notifications']].map(([Icon, text]) => (
        <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 12, fontWeight: 700 }}>
          <span style={{ width: 38, height: 38, borderRadius: 12, display: 'grid', placeItems: 'center', background: 'rgba(255,255,255,0.14)' }}><Icon size={19} /></span>{text}
        </div>
      ))}
    </div>
  </aside>
);

const strengthLabels = ['Weak', 'Weak', 'Fair', 'Good', 'Strong'];
const strengthColors = ['#E2E8F0', 'var(--color-danger)', 'var(--color-warning)', '#EAB308', 'var(--color-success)'];

export default function RegisterPage() {
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '', timezone: 'Asia/Kolkata', notificationPreference: 'BOTH' });
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const auth = useAuth();
  const navigate = useNavigate();

  const strength = useMemo(() => {
    return [form.password.length >= 8, /[A-Z]/.test(form.password), /\d/.test(form.password), /[^A-Za-z0-9]/.test(form.password)].filter(Boolean).length;
  }, [form.password]);

  const setField = (key, value) => setForm((current) => ({ ...current, [key]: value }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    if (!form.name || !form.email || !form.password || !form.confirm || !form.timezone) return setError('Please fill in every required field.');
    if (form.password.length < 8) return setError('Password must be at least 8 characters.');
    if (form.password !== form.confirm) return setError('Passwords do not match.');

    setIsLoading(true);
    try {
      const registerResponse = await api.post('/api/auth/register', {
        name: form.name,
        email: form.email,
        password: form.password,
        timezone: form.timezone,
      });
      const authData = unwrapData(registerResponse.data);
      setTokens(authData.accessToken, authData.refreshToken);
      await api.put('/api/users/me', { notificationPreference: form.notificationPreference });
      const profileResponse = await api.get('/api/users/me');
      auth.login(authData, unwrapData(profileResponse.data));
      navigate('/calendar');
    } catch (err) {
      if (!err.response) setError('Server unavailable. Please start the backend and try again.');
      else if (err.response.status === 409) setError('This email is already registered. Sign in instead?');
      else setError(err.response.data?.message || 'Registration failed. Please check your details.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="auth-page login-split">
      <BrandPanel />
      <section className="auth-form-wrap">
        <form className="card auth-card modal-enter" onSubmit={handleSubmit}>
          <div className="logo-mark" style={{ marginBottom: 18 }}><CalendarCheck size={22} /></div>
          <h2 style={{ fontSize: 30, marginBottom: 8 }}>Create account</h2>
          <p className="muted" style={{ marginTop: 0, marginBottom: 24 }}>Start scheduling with less back-and-forth.</p>
          {error ? <div className="alert alert-danger" style={{ marginBottom: 16 }}>{error}</div> : null}
          <div style={{ display: 'grid', gap: 14 }}>
            <Input label="Full Name" placeholder="Alice Smith" value={form.name} onChange={(e) => setField('name', e.target.value)} required />
            <Input label="Email" type="email" placeholder="you@company.com" value={form.email} onChange={(e) => setField('email', e.target.value)} required />
            <div style={{ position: 'relative' }}>
              <Input label="Password" type={showPassword ? 'text' : 'password'} value={form.password} onChange={(e) => setField('password', e.target.value)} required />
              <button type="button" className="btn-ghost" onClick={() => setShowPassword((value) => !value)} style={{ position: 'absolute', right: 8, top: 34, padding: 8 }}>
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
                <div style={{ flex: 1, height: 7, borderRadius: 999, background: '#E2E8F0', overflow: 'hidden' }}>
                  <div style={{ width: `${strength * 25}%`, height: '100%', background: strengthColors[strength], transition: 'all 0.2s ease' }} />
                </div>
                <span style={{ fontSize: 12, color: strengthColors[strength], fontWeight: 800 }}>{strengthLabels[strength]}</span>
              </div>
            </div>
            <Input label="Confirm Password" type="password" value={form.confirm} onChange={(e) => setField('confirm', e.target.value)} required />
            <div><span className="label">Timezone</span><TimezoneSelect value={form.timezone} onChange={(value) => setField('timezone', value)} /></div>
            <div>
              <span className="label">Notification Preference</span>
              <div className="segmented">
                {preferences.map((pref) => (
                  <button key={pref.value} type="button" className={`segment ${form.notificationPreference === pref.value ? 'active' : ''}`} onClick={() => setField('notificationPreference', pref.value)}>
                    {pref.label}
                  </button>
                ))}
              </div>
            </div>
            <Button type="submit" loading={isLoading}>Create Account</Button>
          </div>
          <p style={{ textAlign: 'center', marginTop: 18, color: 'var(--color-text-secondary)' }}>
            Already have an account? <Link to="/login" style={{ color: 'var(--color-primary)', fontWeight: 800 }}>Sign in</Link>
          </p>
        </form>
      </section>
    </main>
  );
}
