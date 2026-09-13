import { useEffect, useState } from 'react';
import api from '../api/client';
import { Slot, SlotEvent, Zone } from '../api/types';

export default function Slots() {
  const [slots, setSlots] = useState<Slot[]>([]);
  const [zones, setZones] = useState<Zone[]>([]);
  const [destinationZoneId, setDestinationZoneId] = useState('');
  const [message, setMessage] = useState('');

  async function refresh() {
    const [slotsRes, zonesRes] = await Promise.all([
      api.get<Slot[]>('/api/slots'),
      api.get<Zone[]>('/api/zones'),
    ]);
    setSlots(slotsRes.data);
    setZones(zonesRes.data);
  }

  useEffect(() => { refresh(); }, []);

  async function claimSlot(slot: Slot) {
    setMessage('');
    try {
      // find the active event that released this slot
      const eventsRes = await api.get<SlotEvent[]>('/api/slot-events', { params: { slotId: slot.id } });
      const activeEvent = eventsRes.data.find((e) => e.status === 'ACTIVE');
      if (!activeEvent) {
        setMessage('No active release event found for this slot.');
        return;
      }
      await api.post('/api/claims', {
        eventId: activeEvent.id,
        destinationZoneId: destinationZoneId || null,
      });
      setMessage(`Claimed ${slot.slotCode} successfully.`);
      refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not claim this slot.');
    }
  }

  const available = slots.filter((s) => s.status === 'AVAILABLE');
  const others = slots.filter((s) => s.status !== 'AVAILABLE');

  return (
    <div>
      <h1>Parking Slots</h1>

      <section>
        <h2>Available to claim</h2>
        <p className="muted small">
          This claims a slot temporarily released by its owner (via an absence or vehicle-change
          event). To pick an unowned slot as your own permanent slot instead, use the Dashboard.
        </p>
        <label>Your destination zone (optional — for your own reference only; it does not affect who wins the claim)</label>
        <select value={destinationZoneId} onChange={(e) => setDestinationZoneId(e.target.value)}>
          <option value="">— none —</option>
          {zones.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
        </select>
        {message && <p className="hint">{message}</p>}
        <div className="slot-grid">
          {available.length === 0 && <p className="muted">No slots are currently available.</p>}
          {available.map((s) => (
            <div key={s.id} className="slot-card status-available">
              <strong>{s.slotCode}</strong>
              <span>{s.zoneName}</span>
              <span>{s.vehicleType.replace('_', ' ')}</span>
              <button onClick={() => claimSlot(s)}>Claim</button>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2>All slots</h2>
        <div className="slot-grid">
          {others.map((s) => (
            <div key={s.id} className={`slot-card status-${s.status.toLowerCase()}`}>
              <strong>{s.slotCode}</strong>
              <span>{s.zoneName}</span>
              <span>{s.vehicleType.replace('_', ' ')}</span>
              <span className="badge">{s.status}</span>
              <span className="muted small">{s.assignedToName ?? 'Unassigned'}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
