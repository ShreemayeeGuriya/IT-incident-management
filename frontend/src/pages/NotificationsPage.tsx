import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { notificationsApi } from "../api/endpoints";
import { ApiErrorMessage, Empty, Loading, PageHeader } from "../components/ui";
import { fmtDate } from "../utils/format";
import type { NotificationItem, Page } from "../types/incident";

export default function NotificationsPage() {
  const [data, setData] = useState<Page<NotificationItem> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const [pageNo, setPageNo] = useState(0);
  const refresh = () => notificationsApi.list(pageNo).then(setData).catch(setError);

  useEffect(() => {
    setData(null);
    refresh();
  }, [pageNo]);

  const mark = async (id: number) => {
    try {
      setActionError(null);
      await notificationsApi.markRead(id);
      await refresh();
    } catch (e) {
      setActionError(e);
    }
  };

  return (
    <div className="page">
      <PageHeader eyebrow="Your inbox" title="Notifications" />
      {actionError != null && <ApiErrorMessage error={actionError} />}
      {error ? <ApiErrorMessage error={error} /> : !data ? <Loading /> : (
        <section className="panel notification-list">
          {data.content.length ? data.content.map((n) => (
            <div className={`notification ${n.isRead ? "read" : "unread"}`} key={n.id}>
              <span className="notification-mark">{n.isRead ? "·" : "●"}</span>
              <div className="notification-copy">
                <div><strong>{n.notificationType.replaceAll("_", " ")}</strong><span>{fmtDate(n.createdAt)}</span></div>
                <p>{n.message}</p>
                {n.incidentId && <Link to={`/incidents/${n.incidentId}`}>Open incident #{n.incidentId} →</Link>}
              </div>
              {!n.isRead && <button className="btn secondary small-btn" onClick={() => mark(n.id)}>Mark read</button>}
            </div>
          )) : <Empty title="You’re all caught up" />}
          <div className="pagination">
            <button className="btn secondary small-btn" disabled={pageNo <= 0} onClick={() => setPageNo((n) => n - 1)}>← Previous</button>
            <span>Page {pageNo + 1} of {Math.max(data.totalPages, 1)}</span>
            <button className="btn secondary small-btn" disabled={pageNo + 1 >= data.totalPages} onClick={() => setPageNo((n) => n + 1)}>Next →</button>
          </div>
        </section>
      )}
    </div>
  );
}
