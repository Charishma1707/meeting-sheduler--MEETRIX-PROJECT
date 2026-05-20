import { Bell, CalendarClock, CalendarPlus, CalendarX, Clock, UserCheck } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { formatDistanceToNow } from 'date-fns';

const iconMap = {
  INVITE_RECEIVED: [CalendarPlus, 'var(--color-primary)'],
  MEETING_CANCELLED: [CalendarX, 'var(--color-danger)'],
  MEETING_UPDATED: [CalendarClock, 'var(--color-warning)'],
  RSVP_UPDATE: [UserCheck, 'var(--color-success)'],
  REMINDER: [Clock, 'var(--color-warning)'],
};

export default function NotificationBell({ notifications = [], onClear }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const close = (event) => {
      if (ref.current && !ref.current.contains(event.target)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="btn btn-secondary icon-btn" onClick={() => setOpen((value) => !value)} aria-label="Notifications">
        <Bell size={19} />
        {notifications.length ? (
          <span style={{ position: 'absolute', top: -4, right: -4, minWidth: 20, height: 20, borderRadius: 999, background: 'var(--color-danger)', color: '#fff', fontSize: 11, display: 'grid', placeItems: 'center', fontWeight: 800 }}>
            {notifications.length > 9 ? '9+' : notifications.length}
          </span>
        ) : null}
      </button>
      {open ? (
        <div className="notification-menu">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 16, borderBottom: '1px solid var(--color-border)' }}>
            <strong>Notifications</strong>
            <button className="btn-ghost" type="button" onClick={onClear} style={{ border: 0, background: 'transparent', color: 'var(--color-primary)', fontWeight: 800 }}>Clear all</button>
          </div>
          <div style={{ maxHeight: 420, overflow: 'auto' }}>
            {!notifications.length ? <div className="muted" style={{ padding: 18, textAlign: 'center' }}>No new notifications</div> : null}
            {notifications.slice(0, 10).map((item, index) => {
              const [Icon, color] = iconMap[item.type] || [Bell, 'var(--color-info)'];
              return (
                <div key={`${item.timestamp}-${index}`} style={{ display: 'flex', gap: 12, padding: 14, borderBottom: '1px solid var(--color-border)' }}>
                  <Icon size={20} color={color} />
                  <div style={{ flex: 1 }}>
                    <strong style={{ display: 'block', fontSize: 14 }}>{item.title}</strong>
                    <span className="muted" style={{ fontSize: 13 }}>{item.message}</span>
                    <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                      {item.timestamp ? formatDistanceToNow(new Date(item.timestamp), { addSuffix: true }) : 'just now'}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
