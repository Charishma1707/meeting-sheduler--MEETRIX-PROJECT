import { CalendarDays, LogOut, Settings, Users } from 'lucide-react';
import { avatarColor, initials } from '../shared/InviteeSearch';

const tzLabel = (timezone = 'UTC') => timezone.split('/').pop()?.replace('_', ' ') || timezone;

export default function Sidebar({ user, open, activePanel, onFindTime, onSettings, onLogout, onClose }) {
  return (
    <>
      {open ? <div className="sidebar-overlay" onClick={onClose} /> : null}
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
          <div className="logo-mark"><CalendarDays size={22} /></div>
          <div>
            <h2 style={{ fontSize: 20 }}>MeetSync</h2>
            <span className="muted" style={{ fontSize: 12 }}>Meeting Scheduler</span>
          </div>
        </div>
        <div className="card" style={{ padding: 14, display: 'flex', gap: 10, alignItems: 'center' }}>
          <span className="avatar" style={{ background: avatarColor(user?.name) }}>{initials(user?.name)}</span>
          <div style={{ minWidth: 0 }}>
            <strong style={{ display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{user?.name}</strong>
            <span className="muted" style={{ fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', display: 'block' }}>{user?.email}</span>
          </div>
        </div>
        <div className="badge badge-both" style={{ justifyContent: 'flex-start' }}>My Timezone: {tzLabel(user?.timezone)}</div>
        <nav style={{ display: 'grid', gap: 6 }}>
          <button className="sidebar-link active" onClick={onClose}><CalendarDays size={18} />Calendar</button>
          <button className={`sidebar-link ${activePanel === 'availability' ? 'active' : ''}`} onClick={onFindTime}><Users size={18} />Find Time</button>
          <button className="sidebar-link" onClick={onSettings}><Settings size={18} />Settings</button>
          <button className="sidebar-link" onClick={onLogout}><LogOut size={18} />Sign Out</button>
        </nav>
        <div style={{ marginTop: 'auto', display: 'grid', gap: 9, fontSize: 13 }}>
          <strong>Calendar Legend</strong>
          {[
            ['My Events', 'var(--color-event-organizer)'],
            ['Accepted', 'var(--color-event-accepted)'],
            ['Pending', 'var(--color-event-pending)'],
            ['Cancelled', 'var(--color-event-cancelled)'],
          ].map(([text, color]) => <span key={text}><span style={{ color }}>●</span> {text}</span>)}
        </div>
      </aside>
    </>
  );
}
