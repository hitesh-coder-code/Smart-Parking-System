import { FormEvent, useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Slot, SlotEvent } from '../api/types';

function toIso(localDateTime: string): string {
  return new Date(localDateTime).toISOString();
}

export default function Events() {
  const { user } = useAuth();
  const [mySlots, setMySlots] = useState<Slot[]>([]);
  const [events, setEvents] = useState<SlotEvent[]>([]);
  const [slotId, setSlotId] = useState('');
  const [eventType, setEventType] = useState<'ABSENCE' | 'VEHICLE_CHANGE'>('ABSENCE');
  const [startAt, setStartAt] = useState('');
  const [endAt, setEndAt] = useState('');
  const [message, setMessage] = useState('');

  async function refresh() {
    const slotsRes = await api.get<Slot[]>('/api/slots');
    const owned = slotsRes.data.filter((s) => s.originalOwnerId === user?.userId);
    setMySlots(owned);
    if (owned.length && !slotId) setSlotId(owned[0].id);

    const eventLists = await Promise.all(
      owned.map((s) => api.get<SlotEvent[]>('/api/slot-events', { params: { slotId: s.id } }))
    );
    setEvents(eventLists.flatMap((r) => r.data));
  }

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [user]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setMessage('');
    const path = eventType === 'ABSENCE' ? '/api/slot-events/absence' : '/api/slot-events/vehicle-change';
    try {
      await api.post(path, { slotId, eventType, startAt: toIso(startAt), endAt: toIso(endAt) });
      setMessage('Event reported. The slot has been released.');
      refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not report this event.');
    }
  }

  async function cancelEvent(id: string) {
    await api.patch(`/api/slot-events/${id}/cancel`);
    refresh();
  }

  return (
    <div>
      <h1>My Events</h1>

      {mySlots.length === 0 ? (
        <p className="muted">You don't own a slot to report events for.</p>
      ) : (
        <form className="card" onSubmit={handleSubmit}>
          <h2>Report absence or vehicle change</h2>
          <label>Slot</label>
          <select value={slotId} onChange={(e) => setSlotId(e.target.value)}>
            {mySlots.map((s) => <option key={s.id} value={s.id}>{s.slotCode} ({s.vehicleType})</option>)}
          </select>
          <label>Event type</label>
          <select value={eventType} onChange={(e) => setEventType(e.target.value as any)}>
            <option value="ABSENCE">Absence (I won't be in today)</option>
            <option value="VEHICLE_CHANGE">Vehicle change (arriving on 2-wheeler instead)</option>
          </select>
          <label>From</label>
          <input type="datetime-local" value={startAt} onChange={(e) => setStartAt(e.target.value)} required />
          <label>Until</label>
          <input type="datetime-local" value={endAt} onChange={(e) => setEndAt(e.target.value)} required />
          {message && <p className="hint">{message}</p>}
          <button type="submit">Submit</button>
        </form>
      )}

      <h2>Event history</h2>
      <table className="table">
        <thead>
          <tr><th>Slot</th><th>Type</th><th>From</th><th>Until</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {events.map((e) => (
            <tr key={e.id}>
              <td>{e.slotCode}</td>
              <td>{e.eventType}</td>
              <td>{new Date(e.startAt).toLocaleString()}</td>
              <td>{new Date(e.endAt).toLocaleString()}</td>
              <td>{e.status}</td>
              <td>{e.status === 'ACTIVE' && <button onClick={() => cancelEvent(e.id)}>Cancel</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
