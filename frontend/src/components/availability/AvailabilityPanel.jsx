import { Check, X } from 'lucide-react';
import { useState } from 'react';
import api, { getErrorMessage, unwrapData } from '../../api/axios';
import { useAuth } from '../../hooks/useAuth';
import Button from '../shared/Button';
import Input from '../shared/Input';
import InviteeSearch from '../shared/InviteeSearch';
import TimezoneSelect from '../shared/TimezoneSelect';

const today = () => new Date().toISOString().slice(0, 10);

export default function AvailabilityPanel({ isOpen, onClose, onSlotSelect }) {
  const { user } = useAuth();
  const [invitees, setInvitees] = useState([]);
  const [form, setForm] = useState({ date: today(), fromTime: '09:00', toTime: '18:00', duration: 60, timezone: user?.timezone || 'UTC' });
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  if (!isOpen) return null;

  const setField = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const addInvitee = (person) => setInvitees((current) => current.some((item) => item.id === person.id) ? current : [...current, person]);
  const removeInvitee = (id) => setInvitees((current) => current.filter((item) => item.id !== id));

  const findSlots = async () => {
    setLoading(true);
    setError('');
    setSlots([]);
    try {
      const response = await api.post('/api/availability/free-slots', {
        userIds: [user.userId, ...invitees.map((person) => person.id)],
        durationMinutes: Number(form.duration),
        fromLocal: `${form.date}T${form.fromTime}:00`,
        toLocal: `${form.date}T${form.toTime}:00`,
        timezone: form.timezone,
      });
      setSlots(unwrapData(response.data)?.freeSlots || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not find free slots.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <aside className="availability-panel modal-enter">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 20 }}>
        <div><h2>Find Free Time</h2><p className="muted" style={{ margin: '6px 0 0' }}>Search mutual availability.</p></div>
        <button className="btn btn-secondary icon-btn" onClick={onClose}><X size={18} /></button>
      </div>
      <div style={{ display: 'grid', gap: 18 }}>
        <section>
          <h3 style={{ fontSize: 15, marginBottom: 10 }}>Who needs to attend?</h3>
          <InviteeSearch selectedInvitees={invitees} onAdd={addInvitee} onRemove={removeInvitee} excludeIds={[user.userId]} />
        </section>
        <section>
          <h3 style={{ fontSize: 15, marginBottom: 10 }}>Meeting details</h3>
          <div style={{ display: 'grid', gap: 12 }}>
            <Input label="Date" type="date" value={form.date} onChange={(e) => setField('date', e.target.value)} />
            <div className="form-row">
              <Input label="From" type="time" value={form.fromTime} onChange={(e) => setField('fromTime', e.target.value)} />
              <Input label="To" type="time" value={form.toTime} onChange={(e) => setField('toTime', e.target.value)} />
            </div>
            <div>
              <span className="label">Duration</span>
              <select className="select-input" value={form.duration} onChange={(e) => setField('duration', e.target.value)}>
                {[15, 30, 45, 60, 90, 120].map((value) => <option key={value} value={value}>{value} minutes</option>)}
              </select>
            </div>
            <div><span className="label">Timezone</span><TimezoneSelect value={form.timezone} onChange={(value) => setField('timezone', value)} /></div>
          </div>
        </section>
        {error ? <div className="alert alert-danger">{error}</div> : null}
        <Button onClick={findSlots} loading={loading}>Find Free Slots</Button>
        <section>
          <h3 style={{ fontSize: 15, marginBottom: 10 }}>Results</h3>
          {loading ? Array.from({ length: 4 }).map((_, index) => <div className="skeleton" key={index} style={{ height: 74, marginBottom: 10 }} />) : null}
          {!loading && slots.length === 0 ? (
            <div className="card" style={{ padding: 18, textAlign: 'center' }}>
              <strong>No free slots found for this window.</strong>
              <p className="muted" style={{ marginBottom: 0 }}>Try a wider time range or fewer attendees.</p>
            </div>
          ) : null}
          {!loading && slots.map((slot) => (
            <div key={`${slot.startLocal}-${slot.endLocal}`} className="card" style={{ padding: 14, marginBottom: 10, display: 'flex', alignItems: 'center', gap: 12 }}>
              <Check color="var(--color-success)" />
              <div style={{ flex: 1 }}>
                <strong>{slot.startLocal.slice(11, 16)} - {slot.endLocal.slice(11, 16)}</strong>
                <div className="muted" style={{ fontSize: 13 }}>{slot.startLocal.slice(0, 10)} · {slot.durationMinutes} min</div>
              </div>
              <Button variant="secondary" onClick={() => onSlotSelect({ startTimeLocal: slot.startLocal, endTimeLocal: slot.endLocal, timezone: form.timezone })}>Use This Time</Button>
            </div>
          ))}
        </section>
      </div>
    </aside>
  );
}
