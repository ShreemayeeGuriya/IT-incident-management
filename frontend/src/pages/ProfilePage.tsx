import { useAuth } from "../auth/AuthContext";
import { PageHeader } from "../components/ui";
import { fmtDate } from "../utils/format";
export default function ProfilePage() { const { user } = useAuth(); return <div className="page"><PageHeader eyebrow="Account" title="Your profile" /><section className="panel profile-card"><div className="avatar large">{user?.name.split(" ").map((x) => x[0]).slice(0, 2).join("").toUpperCase()}</div><h2>{user?.name}</h2><p>{user?.email}</p><div className="fact-grid"><div className="fact"><span>Role</span><strong>{user?.role}</strong></div><div className="fact"><span>Account created</span><strong>{fmtDate(user?.createdAt)}</strong></div><div className="fact"><span>Account ID</span><strong>{user?.id}</strong></div></div></section></div>; }
