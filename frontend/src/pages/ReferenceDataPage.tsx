import { useEffect, useState } from "react";
import { lookupApi } from "../api/endpoints";
import { ApiErrorMessage, Empty, Loading, PageHeader } from "../components/ui";
import type { Lookup } from "../types/incident";

export default function ReferenceDataPage() {
  const [categories, setCategories] = useState<Lookup[] | null>(null);
  const [services, setServices] = useState<Lookup[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    Promise.all([lookupApi.categories(), lookupApi.services()])
      .then(([categoryRows, serviceRows]) => {
        setCategories(categoryRows);
        setServices(serviceRows);
      })
      .catch(setError);
  }, []);

  if (error) return <div className="page"><PageHeader eyebrow="Administration" title="Categories & services" /><ApiErrorMessage error={error} /></div>;
  if (!categories || !services) return <div className="page"><PageHeader eyebrow="Administration" title="Categories & services" /><Loading label="Loading reference data…" /></div>;

  return <div className="page">
    <PageHeader eyebrow="Administration" title="Categories & services" />
    <div className="reference-grid">
      <ReferenceList title="Incident categories" items={categories} />
      <ReferenceList title="IT services" items={services} />
    </div>
  </div>;
}

function ReferenceList({ title, items }: { title: string; items: Lookup[] }) {
  return <section className="panel reference-panel">
    <div className="panel-head"><div><div className="eyebrow">Reference data</div><h2>{title}</h2></div><span className="reference-count">{items.length}</span></div>
    {items.length ? <div className="reference-list">{items.map((item) => <div className="reference-item" key={item.id}><span className="reference-dot" /><div><strong>{item.name}</strong>{item.description && <p>{item.description}</p>}</div></div>)}</div> : <Empty title={`No ${title.toLowerCase()} found`} />}
  </section>;
}
