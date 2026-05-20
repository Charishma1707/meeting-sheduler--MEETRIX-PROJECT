import { CalendarDays, MapPin, RefreshCw, User, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { formatInTimeZone } from 'date-fns-tz';
import toast from 'react-hot-toast';
import api, { getErrorMessage, unwrapData } from '../../api/axios';
import Badge from '../shared/Badge';
import Button from '../shared/Button';
import { avatarColor, initials } from '../shared/InviteeSearch';
import { useAuth } from '../../hooks/useAuth';

const recurrenceText = (recurrence) => {
  if (!recurrence) return '';
  const type = recurrence.type?.toLowerCase() || 'weekly';
  const interval = recurrence.interval || 1;
  const until = recurrence.until ? new Date(recurrence.until).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : 'later';
  return `Repeats every ${interval} ${type}${interval === 1 ? '' : 's'} until ${until}`;
};

export default function EventDetailModal({ isOpen, onClose, event, onEventUpdated, wsLastMessage }) {
  const { user } = useAuth();
  const [current, setCurrent] = useState(event);
  const [loading, setLoading] = useState(false);

  useEffect(() => setCurrent(event), [event]);

  const fetchEvent = async () => {
    if (!current?.id) return;
    const response = await api.get(`/api/events/${current.id}`);
    const data = unwrapData(response.data);
    setCurrent(data);
    return data;
  };

  useEffect(() => {
    if (wsLastMessage?.eventId === current?.id && wsLastMessage?.type === 'RSVP_UPDATE') {
      fetchEvent().catch(() => { });
    }
  }, [wsLastMessage?.eventId, wsLastMessage?.type]);

  if (!isOpen || !current) return null;

  const startText = formatInTimeZone(current.startTimeLocal, current.timezone, 'EEEE, MMMM d');
  const timeText = `${formatInTimeZone(current.startTimeLocal, current.timezone, 'h:mm a')} - ${formatInTimeZone(current.endTimeLocal, current.timezone, 'h:mm a zzz')}`;
  const isOrganizer = current.organizerId === user?.userId || current.myRsvpStatus === 'ORGANIZER';
  const organizerLabel = current.organizerName || current.organizerEmail || current.organizerId;

  const rsvp = async (status) => {
    setLoading(true);
    try {
      const response = await api.post(`/api/events/${current.id}/rsvp`, { status });
      setCurrent(unwrapData(response.data));
      toast.success(`RSVP updated to ${status.toLowerCase()}.`);
      onEventUpdated?.();
    } catch (err) {
      toast.error(getErrorMessage(err, 'RSVP update failed.'));
    } finally {
      setLoading(false);
    }
  };

  const cancel = async () => {
    if (!window.confirm('Cancel this meeting? All invitees will be notified.')) return;
    setLoading(true);
    try {
      await api.delete(`/api/events/${current.id}`);
      toast.success('Meeting cancelled.');
      onEventUpdated?.();
      onClose();
    } catch (err) {
      toast.error(getErrorMessage(err, 'Could not cancel meeting.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <div className="modal-card modal-wide modal-enter">
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
          <h2 style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ width: 10, height: 10, borderRadius: 99, background: current.status === 'ACTIVE' ? 'var(--color-success)' : 'var(--color-event-cancelled)' }} />
            {current.title}
          </h2>
          <button className="btn btn-secondary icon-btn" onClick={onClose}><X size={18} /></button>
        </div>

        {current.status === 'CANCELLED' ? <div className="alert alert-info" style={{ marginTop: 18 }}>This meeting has been cancelled.</div> : null}

        <section className="card" style={{ padding: 16, marginTop: 18, display: 'grid', gap: 12 }}>
          <div style={{ display: 'flex', gap: 10 }}><CalendarDays size={18} color="var(--color-primary)" /><span>{startText} · {timeText}</span></div>
          <div style={{ display: 'flex', gap: 10 }}><MapPin size={18} color="var(--color-primary)" /><span className={!current.location ? 'muted' : ''}>{current.location || 'No location specified'}</span></div>
          {current.recurrence ? <div style={{ display: 'flex', gap: 10 }}><RefreshCw size={18} color="var(--color-primary)" /><span>{recurrenceText(current.recurrence)}</span></div> : null}
          <div style={{ display: 'flex', gap: 10 }}><User size={18} color="var(--color-primary)" /><span>{isOrganizer ? 'Created by you' : `Organized by ${organizerLabel}`}</span></div>
        </section>

        {current.description ? (
          <section style={{ marginTop: 22 }}>
            <h3 style={{ fontSize: 16, marginBottom: 8 }}>About this meeting</h3>
            <p style={{ margin: 0, lineHeight: 1.6, color: 'var(--color-text-secondary)' }}>{current.description}</p>
          </section>
        ) : null}

        <section style={{ marginTop: 22 }}>
          <h3 style={{ fontSize: 16, marginBottom: 12 }}>Invitees ({current.invites?.length || 0})</h3>
          <div className="card" style={{ overflow: 'hidden' }}>
            {current.invites?.length ? current.invites.map((invite) => (
              <div key={invite.inviteeId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, borderBottom: '1px solid var(--color-border)' }}>
                <span className="avatar" style={{ background: avatarColor(invite.inviteeName || invite.inviteeEmail || invite.inviteeId) }}>{initials(invite.inviteeName || invite.inviteeEmail || invite.inviteeId)}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <strong>{invite.inviteeName || invite.inviteeId}</strong><br />
                  <span className="muted" style={{ fontSize: 13 }}>{invite.inviteeEmail || 'No email available'}</span>
                </div>
                <Badge status={invite.status} />
              </div>
            )) : <div className="muted" style={{ padding: 14 }}>No invitees.</div>}
          </div>
        </section>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 24, flexWrap: 'wrap' }}>
          {current.status !== 'CANCELLED' && isOrganizer ? <Button variant="danger" loading={loading} onClick={cancel}>Cancel Meeting</Button> : null}
          {current.status !== 'CANCELLED' && !isOrganizer ? (
            <>
              <Button variant={current.myRsvpStatus === 'ACCEPTED' ? 'primary' : 'secondary'} loading={loading} onClick={() => rsvp('ACCEPTED')}>Accept</Button>
              <Button variant={current.myRsvpStatus === 'DECLINED' ? 'danger' : 'secondary'} loading={loading} onClick={() => rsvp('DECLINED')}>Decline</Button>
            </>
          ) : null}
          <Button variant="secondary" onClick={onClose}>Close</Button>
        </div>
      </div>
    </div>
  );
}
