import { Menu, Plus } from 'lucide-react';
import Button from '../shared/Button';
import NotificationBell from '../notifications/NotificationBell';

export default function TopBar({ title, view, onViewChange, onToday, onNewMeeting, onMenu, notifications, onClearNotifications }) {
  return (
    <header className="topbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <button className="btn btn-secondary icon-btn" onClick={onMenu} aria-label="Open navigation" style={{ display: window.innerWidth <= 767 ? 'inline-flex' : undefined }}>
          <Menu size={19} />
        </button>
        <div>
          <h1 style={{ fontSize: 22 }}>{title}</h1>
          <span className="muted" style={{ fontSize: 13 }}>Calendar workspace</span>
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <Button variant="secondary" onClick={onToday}>Today</Button>
        <div className="view-tabs">
          {[['dayGridMonth', 'Month'], ['timeGridWeek', 'Week'], ['timeGridDay', 'Day']].map(([key, label]) => (
            <button key={key} className={view === key ? 'active' : ''} onClick={() => onViewChange(key)}>{label}</button>
          ))}
        </div>
        <Button onClick={onNewMeeting}><Plus size={18} /> New Meeting</Button>
        <NotificationBell notifications={notifications} onClear={onClearNotifications} />
      </div>
    </header>
  );
}
