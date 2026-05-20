import { Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import api, { getErrorMessage, unwrapData } from '../../api/axios';

const colors = ['#4F46E5', '#10B981', '#F59E0B', '#3B82F6', '#EF4444', '#7C3AED'];

export const initials = (name = '') => name.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || '?';
export const avatarColor = (name = '') => colors[[...name].reduce((sum, char) => sum + char.charCodeAt(0), 0) % colors.length];

export default function InviteeSearch({ selectedInvitees = [], onAdd, onRemove, excludeIds = [] }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const boxRef = useRef(null);

  const excluded = useMemo(() => new Set([...excludeIds, ...selectedInvitees.map((user) => user.id)]), [excludeIds, selectedInvitees]);

  useEffect(() => {
    const close = (event) => {
      if (boxRef.current && !boxRef.current.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setError('');
      return;
    }
    const id = window.setTimeout(async () => {
      setLoading(true);
      setError('');
      try {
        const response = await api.get('/api/users/search', { params: { q: query } });
        setResults((unwrapData(response.data) || []).filter((user) => !excluded.has(user.id)).slice(0, 6));
        setOpen(true);
      } catch (err) {
        setError(getErrorMessage(err, 'Search unavailable'));
      } finally {
        setLoading(false);
      }
    }, 300);
    return () => window.clearTimeout(id);
  }, [query, excluded]);

  return (
    <div ref={boxRef} style={{ position: 'relative' }}>
      <label className="label">Invitees</label>
      <div style={{ position: 'relative' }}>
        <Search size={18} style={{ position: 'absolute', left: 12, top: 13, color: 'var(--color-text-muted)' }} />
        <input className="input" style={{ paddingLeft: 38 }} value={query} onChange={(e) => setQuery(e.target.value)} onFocus={() => setOpen(true)} placeholder="Search by name or email..." />
      </div>

      {selectedInvitees.length ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 10 }}>
          {selectedInvitees.map((user) => (
            <span className="chip" key={user.id}>
              <span className="avatar" style={{ width: 22, height: 22, fontSize: 10, background: avatarColor(user.name) }}>{initials(user.name)}</span>
              {user.name}
              <button type="button" onClick={() => onRemove(user.id)} className="btn-ghost" style={{ border: 0, background: 'transparent', padding: 0, display: 'grid', placeItems: 'center' }} aria-label={`Remove ${user.name}`}>
                <X size={14} />
              </button>
            </span>
          ))}
        </div>
      ) : null}

      {open && query.trim() ? (
        <div className="card" style={{ position: 'absolute', top: 78 + (selectedInvitees.length ? 42 : 0), left: 0, right: 0, zIndex: 30, padding: 8 }}>
          {loading ? Array.from({ length: 3 }).map((_, index) => <div key={index} className="skeleton" style={{ height: 50, margin: 8 }} />) : null}
          {!loading && error ? <div className="alert alert-danger">{error}</div> : null}
          {!loading && !error && results.map((user) => (
            <button key={user.id} type="button" onClick={() => { onAdd(user); setQuery(''); setOpen(false); }} className="btn-ghost" style={{ width: '100%', justifyContent: 'flex-start', padding: 10 }}>
              <span className="avatar" style={{ background: avatarColor(user.name) }}>{initials(user.name)}</span>
              <span style={{ textAlign: 'left', flex: 1 }}>
                <strong>{user.name}</strong><br />
                <span className="muted" style={{ fontSize: 13 }}>{user.email}</span>
              </span>
              <span className="badge badge-both badge-sm">{user.timezone}</span>
            </button>
          ))}
          {!loading && !error && !results.length ? <div style={{ padding: 14, color: 'var(--color-text-muted)' }}>No users found for &quot;{query}&quot;</div> : null}
        </div>
      ) : null}
    </div>
  );
}
