import { FormEvent, useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Slot, Zone, OrgUser } from '../api/types';

export default function Admin() {
  const { user } = useAuth();
  const [zones, setZones] = useState<Zone[]>([]);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [users, setUsers] = useState<OrgUser[]>([]);

  const [zoneName, setZoneName] = useState('');
  const [slotCode, setSlotCode] = useState('');
  const [slotZoneId, setSlotZoneId] = useState('');
  const [slotVehicleType, setSlotVehicleType] = useState<'TWO_WHEELER' | 'FOUR_WHEELER'>('FOUR_WHEELER');
  const [slotOwnerId, setSlotOwnerId] = useState('');
  const [message, setMessage] = useState('');

  async function refresh() {
    try {
      const [zonesRes, slotsRes, usersRes] = await Promise.all([
        api.get<Zone[]>('/api/zones'),
        api.get<Slot[]>('/api/slots'),
        api.get<OrgUser[]>('/api/users'),
      ]);
      setZones(zonesRes.data);
      setSlots(slotsRes.data);
      setUsers(usersRes.data);
      setSlotZoneId((current) => {
        if (current && zonesRes.data.some((z) => z.id === current)) {
          return current;
        }
        return zonesRes.data.length > 0 ? zonesRes.data[0].id : '';
      });
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not load data.');
    }
  }

  useEffect(() => { refresh(); }, []);

  if (user?.role !== 'ADMIN') {
    return <p className="muted">Admin access only.</p>;
  }

  async function createZone(e: FormEvent) {
    e.preventDefault();
    setMessage('');
    try {
      const res = await api.post<Zone>('/api/zones', { name: zoneName });
      const newZone = res.data;
      setZoneName('');
      setMessage(`Zone "${newZone.name}" created successfully.`);
      setZones((prev) => [...prev.filter((z) => z.id !== newZone.id), newZone]);
      setSlotZoneId(newZone.id);
      await refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not create zone.');
    }
  }

  async function createSlot(e: FormEvent) {
    e.preventDefault();
    setMessage('');
    if (!slotZoneId) {
      setMessage('Please select a zone before creating a slot.');
      return;
    }
    try {
      await api.post('/api/slots', {
        zoneId: slotZoneId,
        slotCode,
        vehicleType: slotVehicleType,
        assignedToUserId: slotOwnerId || null,
      });
      setSlotCode('');
      setMessage(`Slot "${slotCode}" created successfully.`);
      await refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not create slot.');
    }
  }

  async function deleteSlot(id: string) {
    await api.delete(`/api/slots/${id}`);
    await refresh();
  }

  return (
    <div>
      <h1>Admin</h1>
      {message && <p className="hint">{message}</p>}

      <div className="two-col">
        <form className="card" onSubmit={createZone}>
          <h2>Create a zone</h2>
          <label>Name</label>
          <input value={zoneName} onChange={(e) => setZoneName(e.target.value)} required />
          <button type="submit">Add zone</button>
        </form>

        <form className="card" onSubmit={createSlot}>
          <h2>Create a slot</h2>
          <label>Slot code</label>
          <input value={slotCode} onChange={(e) => setSlotCode(e.target.value)} required />
          <label>Zone</label>
          <select
            value={slotZoneId}
            onChange={(e) => setSlotZoneId(e.target.value)}
            required
            disabled={zones.length === 0}
          >
            {zones.length === 0 && <option value="">No zones created yet</option>}
            {zones.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
          </select>
          <label>Vehicle type</label>
          <select value={slotVehicleType} onChange={(e) => setSlotVehicleType(e.target.value as any)}>
            <option value="FOUR_WHEELER">Four-wheeler</option>
            <option value="TWO_WHEELER">Two-wheeler</option>
          </select>
          <label>Owner (optional — leave blank for an unassigned pool slot)</label>
          <select value={slotOwnerId} onChange={(e) => setSlotOwnerId(e.target.value)}>
            <option value="">— unassigned —</option>
            {users.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
          </select>
          <button type="submit" disabled={zones.length === 0}>Add slot</button>
        </form>
      </div>

      <h2>Zones ({zones.length})</h2>
      {zones.length === 0 ? (
        <p className="muted">No zones created yet.</p>
      ) : (
        <table className="table">
          <thead><tr><th>Zone Name</th><th>Zone ID</th></tr></thead>
          <tbody>
            {zones.map((z) => (
              <tr key={z.id}>
                <td><strong>{z.name}</strong></td>
                <td><code>{z.id}</code></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>All slots</h2>
      <table className="table">
        <thead><tr><th>Code</th><th>Zone</th><th>Type</th><th>Status</th><th>Owner</th><th /></tr></thead>
        <tbody>
          {slots.map((s) => (
            <tr key={s.id}>
              <td>{s.slotCode}</td><td>{s.zoneName}</td><td>{s.vehicleType}</td>
              <td>{s.status}</td><td>{s.assignedToName ?? '—'}</td>
              <td><button onClick={() => deleteSlot(s.id)}>Delete</button></td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>Users in this organization</h2>
      <table className="table">
        <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Default vehicle</th></tr></thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}><td>{u.name}</td><td>{u.email}</td><td>{u.role}</td><td>{u.defaultVehicleType}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
