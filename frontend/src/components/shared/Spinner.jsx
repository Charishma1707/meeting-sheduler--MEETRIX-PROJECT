export default function Spinner({ dark = false }) {
  return <span className={`spinner ${dark ? 'dark' : ''}`} aria-label="Loading" />;
}
