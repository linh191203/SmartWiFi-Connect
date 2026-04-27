export default function EmptyState({ title, body, action }) {
  return (
    <section className="panel empty-state">
      <p className="eyebrow">Nothing here yet</p>
      <h3>{title}</h3>
      <p className="muted">{body}</p>
      {action}
    </section>
  );
}