import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Slot } from '../api/types';

export default function Dashboard() {
  const { user } = useAuth();
  const [mySlots, setMySlots] = useState<Slot[]>([]);
  const [poolSlots, setPoolSlots] = useState<Slot[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  async function refresh() {
    setLoading(true);
    const res = await api.get<Slot[]>('/api/slots');
    setMySlots(res.data.filter((s) => s.originalOwnerId === user?.userId));
    // unowned pool slots eligible to become someone's permanent slot
    setPoolSlots(res.data.filter((s) => s.originalOwnerId === null && s.status === 'AVAILABLE'));
    setLoading(false);
  }

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [user]);

  async function claimPermanent(slotId: string) {
    setMessage('');
    try {
      await api.post(`/api/slots/${slotId}/claim-permanent`);
      setMessage('Slot assigned as your permanent slot.');
      refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not claim this slot.');
    }
  }

  async function releaseSlot(slotId: string) {
    setMessage('');
    try {
      await api.post(`/api/slots/${slotId}/release`);
      setMessage('Slot released back to the unowned pool.');
      refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not release this slot.');
    }
  }

  return (
    <div>
      <h1>Welcome, {user?.name}</h1>
      <p className="muted">{user?.role} · Org ID: {user?.orgId}</p>
      {message && <p className="hint">{message}</p>}

      <h2>Your permanent parking slot</h2>
      {loading && <p>Loading…</p>}
      {!loading && mySlots.length === 0 && (
        <p className="muted">You don't have a permanent slot yet — pick one below.</p>
      )}
      <div className="slot-grid">
        {mySlots.map((s) => (
          <div key={s.id} className={`slot-card status-${s.status.toLowerCase()}`}>
            <strong>{s.slotCode}</strong>
            <span>{s.zoneName}</span>
            <span>{s.vehicleType.replace('_', ' ')}</span>
            <span className="badge">{s.status}</span>
            {s.assignedToUserId !== s.originalOwnerId && (
              <span className="muted small">Currently held by {s.assignedToName ?? 'no one'}</span>
            )}
            {s.assignedToUserId === s.originalOwnerId && (
              <button onClick={() => releaseSlot(s.id)}>Release this slot</button>
            )}
          </div>
        ))}
      </div>

      {!loading && mySlots.length === 0 && (
        <>
          <h2>Available slots to claim permanently</h2>
          <div className="slot-grid">
            {poolSlots.length === 0 && <p className="muted">No unowned slots are currently available.</p>}
            {poolSlots.map((s) => (
              <div key={s.id} className="slot-card status-available">
                <strong>{s.slotCode}</strong>
                <span>{s.zoneName}</span>
                <span>{s.vehicleType.replace('_', ' ')}</span>
                <button onClick={() => claimPermanent(s.id)}>Make this mine</button>
              </div>
            ))}
          </div>
        </>
      )}

      <p className="muted">
        Head to <strong>My Events</strong> to report an absence or vehicle change on your slot,
        or <strong>Swaps</strong> to arrange an exchange with a colleague.
      </p>
    </div>
  );
}
