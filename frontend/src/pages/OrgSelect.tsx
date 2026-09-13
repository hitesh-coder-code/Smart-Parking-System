import { FormEvent, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext';

interface Org {
  id: string;
  name: string;
  type: string;
}

// Mirrors OrganizationService.JOINABLE_ROLES on the backend — ADMIN is never joinable,
// it's only granted automatically to whoever creates the organization.
const JOINABLE_ROLES: Record<string, string[]> = {
  COLLEGE: ['FACULTY', 'STAFF'],
  SHOPPING_COMPLEX: ['STAFF', 'CUSTOMER'],
  APARTMENT: ['RESIDENT'],
};

export default function OrgSelect() {
  const { refreshUser } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'join' | 'create'>('join');
  const [orgs, setOrgs] = useState<Org[]>([]);
  const [error, setError] = useState('');

  // join form state
  const [selectedOrgId, setSelectedOrgId] = useState('');
  const [joinRole, setJoinRole] = useState('');
  const [joinVehicle, setJoinVehicle] = useState<'FOUR_WHEELER' | 'TWO_WHEELER'>('FOUR_WHEELER');

  // create form state
  const [orgName, setOrgName] = useState('');
  const [orgType, setOrgType] = useState<'COLLEGE' | 'SHOPPING_COMPLEX' | 'APARTMENT'>('COLLEGE');
  const [createVehicle, setCreateVehicle] = useState<'FOUR_WHEELER' | 'TWO_WHEELER'>('FOUR_WHEELER');

  useEffect(() => {
    api.get<Org[]>('/api/orgs/public').then((res) => {
      setOrgs(res.data);
      if (res.data.length) {
        setSelectedOrgId(res.data[0].id);
        setJoinRole(JOINABLE_ROLES[res.data[0].type]?.[0] || '');
      }
    });
  }, []);

  const selectedOrg = orgs.find((o) => o.id === selectedOrgId);
  const availableJoinRoles = selectedOrg ? JOINABLE_ROLES[selectedOrg.type] || [] : [];

  function handleOrgChange(id: string) {
    setSelectedOrgId(id);
    const org = orgs.find((o) => o.id === id);
    setJoinRole(org ? (JOINABLE_ROLES[org.type]?.[0] || '') : '');
  }

  async function handleJoin(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await api.post(`/api/orgs/${selectedOrgId}/join`, { role: joinRole, defaultVehicleType: joinVehicle });
      await refreshUser();
      navigate('/dashboard');
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Could not join this organization');
    }
  }

  async function handleCreate(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      await api.post('/api/orgs', { name: orgName, type: orgType, defaultVehicleType: createVehicle });
      await refreshUser();
      navigate('/admin');
    } catch (err: any) {
      setError(err?.response?.data?.error || 'Could not create this organization');
    }
  }

  return (
    <div className="auth-page">
      <div className="card" style={{ maxWidth: 480 }}>
        <h1>Get started</h1>
        <p className="muted">Join an organization you're already part of, or create a new one.</p>

        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
          <button onClick={() => setMode('join')} disabled={mode === 'join'}>Join existing</button>
          <button onClick={() => setMode('create')} disabled={mode === 'create'}>Create new</button>
        </div>

        {error && <p className="error">{error}</p>}

        {mode === 'join' && (
          <form onSubmit={handleJoin}>
            <label>Organization</label>
            {orgs.length === 0 ? (
              <p className="muted">No organizations exist yet — create the first one.</p>
            ) : (
              <>
                <select value={selectedOrgId} onChange={(e) => handleOrgChange(e.target.value)}>
                  {orgs.map((o) => <option key={o.id} value={o.id}>{o.name} ({o.type})</option>)}
                </select>
                <label>Your role</label>
                <select value={joinRole} onChange={(e) => setJoinRole(e.target.value)}>
                  {availableJoinRoles.map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
                <label>Default vehicle</label>
                <select value={joinVehicle} onChange={(e) => setJoinVehicle(e.target.value as any)}>
                  <option value="FOUR_WHEELER">Four-wheeler</option>
                  <option value="TWO_WHEELER">Two-wheeler</option>
                </select>
                <button type="submit">Join organization</button>
              </>
            )}
          </form>
        )}

        {mode === 'create' && (
          <form onSubmit={handleCreate}>
            <label>Organization name</label>
            <input value={orgName} onChange={(e) => setOrgName(e.target.value)} required />
            <label>Organization type</label>
            <select value={orgType} onChange={(e) => setOrgType(e.target.value as any)}>
              <option value="COLLEGE">College</option>
              <option value="SHOPPING_COMPLEX">Shopping Complex</option>
              <option value="APARTMENT">Apartment</option>
            </select>
            <label>Default vehicle</label>
            <select value={createVehicle} onChange={(e) => setCreateVehicle(e.target.value as any)}>
              <option value="FOUR_WHEELER">Four-wheeler</option>
              <option value="TWO_WHEELER">Two-wheeler</option>
            </select>
            <p className="muted small">You'll become this organization's admin.</p>
            <button type="submit">Create organization</button>
          </form>
        )}
      </div>
    </div>
  );
}
