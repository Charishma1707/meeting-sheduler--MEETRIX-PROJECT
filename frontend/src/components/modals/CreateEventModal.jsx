import { Check, Clock, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import api, { getErrorMessage, unwrapData } from '../../api/axios';
import { useAuth } from '../../hooks/useAuth';
import Button from '../shared/Button';
import Input from '../shared/Input';
import InviteeSearch from '../shared/InviteeSearch';
import TimezoneSelect from '../shared/TimezoneSelect';

const splitLocal = (value) => {
  if (!value) return { date: '', time: '' };
  const clean = value.slice(0, 16);
  const [date, time] = clean.split('T');
  return { date, time };
};
const combine = (date, time) => `${date}T${time || '00:00'}:00`;
const diffMinutes = (startDate, startTime, endDate, endTime) => {
  if (!startDate || !startTime || !endDate || !endTime) return 30;
  return Math.max(1, Math.round((new Date(`${endDate}T${endTime}`) - new Date(`${startDate}T${startTime}`)) / 60000));
};

export default function CreateEventModal({ isOpen, onClose, onSuccess, prefillStart, prefillEnd }) {
  const { user } = useAuth();
  const start = splitLocal(prefillStart);
  const end = splitLocal(prefillEnd);
  const [form, setForm] = useState({
    title: '', description: '', location: '',
    startDate: start.date, startTime: start.time || '09:00',
    endDate: end.date || start.date, endTime: end.time || '09:30',
    timezone: user?.timezone || 'UTC',
    recurrenceType: 'NONE', interval: 1, until: '',
  });
  const [invitees, setInvitees] = useState([]);
  const [showRecurrence, setShowRecurrence] = useState(false);
  const [slots, setSlots] = useState([]);
  const [checking, setChecking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (isOpen) {
      const s = splitLocal(prefillStart);
      const e = splitLocal(prefillEnd);
      setForm((current) => ({
        ...current,
        startDate: s.date || current.startDate,
        startTime: s.time || current.startTime || '09:00',
        endDate: e.date || s.date || current.endDate,
        endTime: e.time || current.endTime || '09:30',
        timezone: user?.timezone || current.timezone || 'UTC',
      }));
      setError('');
    }
  }, [isOpen, prefillStart, prefillEnd, user?.timezone]);

  const duration = useMemo(() => diffMinutes(form.startDate, form.startTime, form.endDate, form.endTime), [form]);
  const setField = (key, value) => setForm((current) => ({ ...current, [key]: value }));
  const addInvitee = (person) => setInvitees((current) => current.some((item) => item.id === person.id) ? current : [...current, person]);
  const removeInvitee = (id) => setInvitees((current) => current.filter((item) => item.id !== id));

  const checkAvailability = async () => {
    if (!invitees.length) return;
    setChecking(true);
    setSlots([]);
    setError('');
    try {
      const response = await api.post('/api/availability/free-slots', {
        userIds: [user.userId, ...invitees.map((person) => person.id)],
        durationMinutes: duration || 30,
        fromLocal: combine(form.startDate, form.startTime || '09:00'),
        toLocal: combine(form.endDate || form.startDate, form.endTime || '18:00'),
        timezone: form.timezone,
      });
      setSlots(unwrapData(response.data)?.freeSlots || []);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not check availability.'));
    } finally {
      setChecking(false);
    }
  };

  const selectSlot = (slot) => {
    const s = splitLocal(slot.startLocal);
    const e = splitLocal(slot.endLocal);
    setForm((current) => ({ ...current, startDate: s.date, startTime: s.time, endDate: e.date, endTime: e.time }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    if (!form.title.trim()) return setError('Title is required.');
    if (new Date(`${form.endDate}T${form.endTime}`) <= new Date(`${form.startDate}T${form.startTime}`)) return setError('End time must be after start time.');
    setSaving(true);
    try {
      const response = await api.post('/api/events', {
        title: form.title,
        description: form.description || null,
        location: form.location || null,
        startTimeLocal: combine(form.startDate, form.startTime),
        endTimeLocal: combine(form.endDate, form.endTime),
        timezone: form.timezone,
        inviteeIds: invitees.map((person) => person.id),
        recurrence: showRecurrence && form.recurrenceType !== 'NONE'
          ? { type: form.recurrenceType, interval: Number(form.interval), until: form.until }
          : null,
      });
      toast.success('Meeting scheduled!');
      onSuccess(unwrapData(response.data));
      onClose();
    } catch (err) {
      if (err.response?.status === 409) setError(`Scheduling Conflict: ${err.response.data?.message || 'A participant is busy.'}`);
      else if (err.response?.status === 400) setError(err.response.data?.message || 'Please check the event fields.');
      else setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="modal-backdrop">
      <form className="modal-card modal-wide modal-enter" onSubmit={submit}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, marginBottom: 20 }}>
          <div><h2>Schedule Meeting</h2><p className="muted" style={{ margin: '6px 0 0' }}>Create a meeting with exact backend fields.</p></div>
          <button type="button" className="btn btn-secondary icon-btn" onClick={onClose}><X size={18} /></button>
        </div>
        {error ? <div className="alert alert-danger" style={{ marginBottom: 16 }}>{error}</div> : null}
        <div style={{ display: 'grid', gap: 16 }}>
          <Input label="Title" placeholder="e.g. Team Standup, Design Review" value={form.title} onChange={(e) => setField('title', e.target.value)} required />
          <Input as="textarea" label="Description" placeholder="Add an agenda or description..." value={form.description} onChange={(e) => setField('description', e.target.value)} />
          <Input label="Location" placeholder="Zoom link, Room 2B, Google Meet..." value={form.location} onChange={(e) => setField('location', e.target.value)} />
          <div className="form-row">
            <Input label="Start Date" type="date" value={form.startDate} onChange={(e) => setField('startDate', e.target.value)} required />
            <Input label="Start Time" type="time" value={form.startTime} onChange={(e) => setField('startTime', e.target.value)} required />
            <Input label="End Date" type="date" value={form.endDate} onChange={(e) => setField('endDate', e.target.value)} required />
            <Input label="End Time" type="time" value={form.endTime} onChange={(e) => setField('endTime', e.target.value)} required />
          </div>
          <div><span className="label">Timezone</span><TimezoneSelect value={form.timezone} onChange={(value) => setField('timezone', value)} /></div>
          <InviteeSearch selectedInvitees={invitees} onAdd={addInvitee} onRemove={removeInvitee} excludeIds={[user.userId]} />
          <div className="card" style={{ padding: 14 }}>
            <button type="button" className="btn-ghost" style={{ background: 'transparent', border: 0, fontWeight: 800 }} onClick={() => setShowRecurrence((value) => !value)}>
              {showRecurrence ? 'Remove Recurrence' : 'Add Recurrence'}
            </button>
            {showRecurrence ? (
              <div style={{ display: 'grid', gap: 12, marginTop: 12 }}>
                <div className="segmented">
                  {['NONE', 'DAILY', 'WEEKLY', 'MONTHLY'].map((type) => <button key={type} type="button" className={`segment ${form.recurrenceType === type ? 'active' : ''}`} onClick={() => setField('recurrenceType', type)}>{type === 'NONE' ? 'None' : type[0] + type.slice(1).toLowerCase()}</button>)}
                </div>
                {form.recurrenceType !== 'NONE' ? (
                  <div className="form-row">
                    <Input label={`Repeat every (${form.recurrenceType.toLowerCase()})`} type="number" min="1" value={form.interval} onChange={(e) => setField('interval', e.target.value)} />
                    <Input label="Until" type="date" value={form.until} onChange={(e) => setField('until', e.target.value)} required />
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
          <div>
            <Button type="button" variant="secondary" loading={checking} disabled={!invitees.length} onClick={checkAvailability}><Clock size={17} /> Check Availability</Button>
            {slots.length ? (
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 12 }}>
                {slots.map((slot) => (
                  <button key={`${slot.startLocal}-${slot.endLocal}`} type="button" className="chip" onClick={() => selectSlot(slot)}>
                    <Check size={14} /> {slot.startLocal.slice(11, 16)} - {slot.endLocal.slice(11, 16)}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={saving}>Schedule Meeting</Button>
          </div>
        </div>
      </form>
    </div>
  );
}
