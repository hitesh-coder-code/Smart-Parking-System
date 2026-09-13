import { FormEvent, useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';
import { Slot, Swap, OrgUser } from '../api/types';

export default function Swaps() {
  const { user } = useAuth();
  const [swaps, setSwaps] = useState<Swap[]>([]);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [users, setUsers] = useState<OrgUser[]>([]);
  const [userBId, setUserBId] = useState('');
  const [slotAId, setSlotAId] = useState('');
  const [slotBId, setSlotBId] = useState('');
  const [recurrenceType, setRecurrenceType] = useState<'ONE_TIME' | 'WEEKLY' | 'DATE_RANGE'>('ONE_TIME');
  const [dayOfWeek, setDayOfWeek] = useState('MONDAY');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [message, setMessage] = useState('');

  async function refresh() {
    const [swapsRes, slotsRes, usersRes] = await Promise.all([
      api.get<Swap[]>('/api/swaps'),
      api.get<Slot[]>('/api/slots'),
      api.get<OrgUser[]>('/api/users'),
    ]);
    setSwaps(swapsRes.data);
    setSlots(slotsRes.data);
    setUsers(usersRes.data.filter((u) => u.id !== user?.userId));
  }

  useEffect(() => { refresh(); /* eslint-disable-next-line */ }, [user]);

  const mySlots = slots.filter((s) => s.originalOwnerId === user?.userId);
  const theirSlots = slots.filter((s) => s.originalOwnerId === userBId);

  async function handlePropose(e: FormEvent) {
    e.preventDefault();
    setMessage('');
    try {
      await api.post('/api/swaps', {
        userBId, slotAId, slotBId, recurrenceType,
        dayOfWeek: recurrenceType === 'WEEKLY' ? dayOfWeek : null,
        startDate: recurrenceType !== 'WEEKLY' ? startDate || null : null,
        endDate: recurrenceType === 'DATE_RANGE' ? endDate || null : null,
      });
      setMessage('Swap proposed. Waiting for the other user to accept.');
      refresh();
    } catch (err: any) {
      setMessage(err?.response?.data?.error || 'Could not propose this swap.');
    }
  }

  async function accept(id: string) {
    await api.patch(`/api/swaps/${id}/accept`);
    refresh();
  }

  async function cancel(id: string) {
    await api.patch(`/api/swaps/${id}/cancel`);
    refresh();
  }

  return (
    <div>
      <h1>Swap Agreements</h1>

      {mySlots.length === 0 ? (
        <p className="muted">You need to own a slot before you can propose a swap.</p>
      ) : (
        <form className="card" onSubmit={handlePropose}>
          <h2>Propose a swap</h2>
          <label>Your slot</label>
          <select value={slotAId} onChange={(e) => setSlotAId(e.target.value)}>
            <option value="">— select —</option>
            {mySlots.map((s) => <option key={s.id} value={s.id}>{s.slotCode}</option>)}
          </select>
          <label>Swap with</label>
          <select value={userBId} onChange={(e) => { setUserBId(e.target.value); setSlotBId(''); }}>
            <option value="">— select a colleague —</option>
            {users.map((u) => <option key={u.id} value={u.id}>{u.name} ({u.email})</option>)}
          </select>
          <label>Their slot</label>
          <select value={slotBId} onChange={(e) => setSlotBId(e.target.value)} disabled={!userBId}>
            <option value="">— select —</option>
            {theirSlots.map((s) => <option key={s.id} value={s.id}>{s.slotCode}</option>)}
          </select>
          <label>Recurrence</label>
          <select value={recurrenceType} onChange={(e) => setRecurrenceType(e.target.value as any)}>
            <option value="ONE_TIME">One-time (a specific date)</option>
            <option value="WEEKLY">Weekly (every same weekday)</option>
            <option value="DATE_RANGE">Date range (e.g. a whole month)</option>
          </select>
          {recurrenceType === 'WEEKLY' && (
            <>
              <label>Day of week</label>
              <select value={dayOfWeek} onChange={(e) => setDayOfWeek(e.target.value)}>
                {['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map((d) => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </>
          )}
          {recurrenceType !== 'WEEKLY' && (
            <>
              <label>Start date</label>
              <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </>
          )}
          {recurrenceType === 'DATE_RANGE' && (
            <>
              <label>End date</label>
              <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
            </>
          )}
          {message && <p className="hint">{message}</p>}
          <button type="submit" disabled={!slotAId || !userBId || !slotBId}>Propose swap</button>
        </form>
      )}

      <h2>Your swaps</h2>
      <table className="table">
        <thead>
          <tr><th>With</th><th>Your slot</th><th>Their slot</th><th>Recurrence</th><th>Status</th><th /></tr>
        </thead>
        <tbody>
          {swaps.map((s) => {
            const isUserA = s.userAId === user?.userId;
            const otherName = isUserA ? s.userBName : s.userAName;
            const mySlotCode = slots.find((sl) => sl.id === (isUserA ? s.slotAId : s.slotBId))?.slotCode;
            const theirSlotCode = slots.find((sl) => sl.id === (isUserA ? s.slotBId : s.slotAId))?.slotCode;
            return (
              <tr key={s.id}>
                <td>{otherName}</td>
                <td>{mySlotCode}</td>
                <td>{theirSlotCode}</td>
                <td>{s.recurrenceType}{s.dayOfWeek ? ` · ${s.dayOfWeek}` : ''}</td>
                <td>{s.status}</td>
                <td>
                  {s.status === 'PENDING' && !isUserA && <button onClick={() => accept(s.id)}>Accept</button>}
                  {(s.status === 'PENDING' || s.status === 'ACTIVE') && <button onClick={() => cancel(s.id)}>Cancel</button>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
