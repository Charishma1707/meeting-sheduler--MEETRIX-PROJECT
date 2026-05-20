export default function Input({ label, error, as = 'input', className = '', ...props }) {
  const Component = as;
  return (
    <label style={{ display: 'block' }}>
      {label ? <span className="label">{label}</span> : null}
      <Component className={`${as === 'textarea' ? 'textarea' : 'input'} ${className}`} {...props} />
      {error ? <div className="field-error">{error}</div> : null}
    </label>
  );
}
