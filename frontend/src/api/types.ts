export interface Slot {
  id: string;
  slotCode: string;
  vehicleType: 'TWO_WHEELER' | 'FOUR_WHEELER';
  status: 'RESERVED' | 'AVAILABLE' | 'CLAIMED';
  zoneId: string;
  zoneName: string;
  assignedToUserId: string | null;
  assignedToName: string | null;
  originalOwnerId: string | null;
}

export interface SlotEvent {
  id: string;
  slotId: string;
  slotCode: string;
  eventType: 'ABSENCE' | 'VEHICLE_CHANGE';
  startAt: string;
  endAt: string;
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
  triggeredByUserId: string;
  tempVehicleSlotId: string | null;
}

export interface Claim {
  id: string;
  eventId: string;
  claimedByUserId: string;
  claimedByName: string;
  proximityRank: number | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
}

export interface Swap {
  id: string;
  userAId: string;
  userAName: string;
  slotAId: string;
  userBId: string;
  userBName: string;
  slotBId: string;
  recurrenceType: 'ONE_TIME' | 'WEEKLY' | 'DATE_RANGE';
  dayOfWeek: string | null;
  startDate: string | null;
  endDate: string | null;
  status: 'PENDING' | 'ACTIVE' | 'CANCELLED' | 'EXPIRED';
}

export interface Zone {
  id: string;
  name: string;
  orgId: string;
}

export interface OrgUser {
  id: string;
  name: string;
  email: string;
  role: string;
  defaultVehicleType: string;
  orgId: string;
}
