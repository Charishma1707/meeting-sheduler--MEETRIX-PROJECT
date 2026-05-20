const labelMap = {
  IN_APP: 'In-App',
};

const clean = (status = '') => labelMap[status] || status.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

export default function Badge({ status, size = 'md' }) {
  const key = String(status || 'unknown').toLowerCase();
  return <span className={`badge badge-${key} badge-${size}`}>{clean(String(status || 'Unknown'))}</span>;
}
