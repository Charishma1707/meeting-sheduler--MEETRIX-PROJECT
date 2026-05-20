import Select from 'react-select';

export const TIMEZONES = [
  { value: 'UTC', label: 'UTC (Coordinated Universal Time)' },
  { value: 'America/New_York', label: 'EST/EDT (New York, Toronto)' },
  { value: 'America/Chicago', label: 'CST/CDT (Chicago, Mexico City)' },
  { value: 'America/Denver', label: 'MST/MDT (Denver, Phoenix)' },
  { value: 'America/Los_Angeles', label: 'PST/PDT (Los Angeles, Seattle)' },
  { value: 'America/Sao_Paulo', label: 'BRT (Sao Paulo, Buenos Aires)' },
  { value: 'Europe/London', label: 'GMT/BST (London, Dublin)' },
  { value: 'Europe/Paris', label: 'CET/CEST (Paris, Berlin, Rome)' },
  { value: 'Europe/Istanbul', label: 'TRT (Istanbul, Ankara)' },
  { value: 'Asia/Dubai', label: 'GST (Dubai, Abu Dhabi)' },
  { value: 'Asia/Kolkata', label: 'IST (India Standard Time)' },
  { value: 'Asia/Bangkok', label: 'ICT (Bangkok, Jakarta)' },
  { value: 'Asia/Singapore', label: 'SGT (Singapore, Kuala Lumpur)' },
  { value: 'Asia/Shanghai', label: 'CST (Beijing, Shanghai)' },
  { value: 'Asia/Tokyo', label: 'JST (Tokyo, Seoul)' },
  { value: 'Australia/Sydney', label: 'AEDT/AEST (Sydney, Melbourne)' },
  { value: 'Pacific/Auckland', label: 'NZST (Auckland, Wellington)' },
];

const styles = {
  control: (base, state) => ({
    ...base,
    minHeight: 44,
    border: `1.5px solid ${state.isFocused ? 'var(--color-primary)' : 'var(--color-border)'}`,
    borderRadius: 'var(--radius-sm)',
    boxShadow: state.isFocused ? '0 0 0 3px var(--color-primary-light)' : 'none',
    '&:hover': { borderColor: 'var(--color-primary)' },
  }),
  option: (base, state) => ({
    ...base,
    background: state.isSelected ? 'var(--color-primary)' : state.isFocused ? 'var(--color-primary-light)' : '#fff',
    color: state.isSelected ? '#fff' : 'var(--color-text-primary)',
  }),
  menu: (base) => ({ ...base, zIndex: 200 }),
};

export default function TimezoneSelect({ value, onChange, disabled = false }) {
  return (
    <Select
      isDisabled={disabled}
      styles={styles}
      options={TIMEZONES}
      value={TIMEZONES.find((tz) => tz.value === value) || TIMEZONES[0]}
      onChange={(option) => onChange(option.value)}
      placeholder="Select timezone"
    />
  );
}
