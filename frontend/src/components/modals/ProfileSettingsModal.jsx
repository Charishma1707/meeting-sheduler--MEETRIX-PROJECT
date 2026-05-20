import { X } from 'lucide-react';
import { useState } from 'react';
import toast from 'react-hot-toast';
import api, { getErrorMessage, unwrapData } from '../../api/axios';
import { useAuth } from '../../hooks/useAuth';
import Button from '../shared/Button';
import Input from '../shared/Input';
import TimezoneSelect from '../shared/TimezoneSelect';

const preferences = [
  { label: 'Email', value: 'EMAIL' },
  { label: 'In-App', value: 'IN_APP' },
  { label: 'Both', value: 'BOTH' },
];

export default function ProfileSettingsModal({ isOpen, onClose }) {
  const { user, updateUser } = useAuth();
  const [form, setForm] = useState({ name: user?.name || '', timezone: user?.timezone || 'UTC', notificationPreference: user?.notificationPreference || 'BOTH' });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  if (!isOpen) return null;

  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    const changed = {};
    if (form.name !== user.name) changed.name = form.name;
    if (form.timezone !== user.timezone) changed.timezone = form.timezone;
    if (form.notificationPreference !== user.notificationPreference) changed.notificationPreference = form.notificationPreference;
    try {
      const response = await api.put('/api/users/me', changed);
      updateUser(unwrapData(response.data));
      toast.success('Profile updated.');
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'Could not update profile.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop">
      <form className="modal-card modal-enter" onSubmit={submit}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 18 }}>
          <h2>Settings</h2>
          <button className="btn btn-secondary icon-btn" type="button" onClick={onClose}><X size={18} /></button>
        </div>
        {error ? <div className="alert alert-danger" style={{ marginBottom: 14 }}>{error}</div> : null}
        <div style={{ display: 'grid', gap: 16 }}>
          <Input label="Name" value={form.name} onChange={(e) => setForm((current) => ({ ...current, name: e.target.value }))} />
          <div><span className="label">Timezone</span><TimezoneSelect value={form.timezone} onChange={(timezone) => setForm((current) => ({ ...current, timezone }))} /></div>
          <div>
            <span className="label">Notification Preference</span>
            <div className="segmented">
              {preferences.map((pref) => <button key={pref.value} type="button" className={`segment ${form.notificationPreference === pref.value ? 'active' : ''}`} onClick={() => setForm((current) => ({ ...current, notificationPreference: pref.value }))}>{pref.label}</button>)}
            </div>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
            <Button type="submit" loading={saving}>Save Changes</Button>
          </div>
        </div>
      </form>
    </div>
  );
}
