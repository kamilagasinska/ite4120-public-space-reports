import { Button, Result } from 'antd';
import { AppTag } from '@helex/ui';
import type { Report, ReportStatus } from '../../reportsApi';

export const STATUS: Record<ReportStatus, { text: string; color: string }> = {
  RECEIVED: { text: 'Received', color: 'gold' },
  ACCEPTED: { text: 'Accepted', color: 'blue' },
  IN_PROGRESS: { text: 'In progress', color: 'orange' },
  RESOLVED: { text: 'Resolved', color: 'green' },
  REJECTED: { text: 'Rejected', color: 'red' },
};

/** The colours of the pins on the map. */
export const DOT: Record<ReportStatus, string> = { RECEIVED: '#f2c230', ACCEPTED: '#2468b0', IN_PROGRESS: '#c45a0a', RESOLVED: '#277257', REJECTED: '#b3203a' };

export type Role = 'coordinator' | 'unit';

/**
 * The same changes the server allows. The coordinator accepts and rejects, the unit the
 * report was handed to solves it. The server decides, this only hides the impossible ones.
 */
export const NEXT: Record<Role, Record<ReportStatus, ReportStatus[]>> = {
  coordinator: { RECEIVED: ['ACCEPTED', 'REJECTED'], ACCEPTED: ['REJECTED'], IN_PROGRESS: [], RESOLVED: [], REJECTED: [] },
  unit: { RECEIVED: [], ACCEPTED: ['IN_PROGRESS'], IN_PROGRESS: ['RESOLVED'], RESOLVED: [], REJECTED: [] },
};

/** What the state of a handover means for people. */
export const HANDOVER: Record<NonNullable<Report['handoverState']>, { text: string; color: string }> = {
  SENT: { text: 'sent to the unit', color: 'blue' },
  OPENED: { text: 'opened by the unit', color: 'green' },
  RETURNED: { text: 'returned by the unit', color: 'red' },
  FAILED: { text: 'not delivered', color: 'red' },
};

/** The inbox address of the unit signed in with the mock sign-in, or null. */
export const signedUnit = () => {
  const user = localStorage.getItem('reports_user') ?? '';
  return user.endsWith('.example') ? user : null;
};

export const CHANNEL: Record<Report['channel'], string> = { GUEST: 'Guest', DEVICE: 'Device', SIGNED: 'Smart-ID' };

export const StatusTag = ({ status }: { status?: ReportStatus }) =>
  status ? <AppTag color={STATUS[status].color}>{STATUS[status].text}</AppTag> : null;

/** Mock auth only. In production the role comes from the real sign-in. */
export const NoAccess = () => (
  <Result
    status="403"
    title="Only for coordinators"
    subTitle="Your user has no coordinator role. In this course sign-in is a mock."
    extra={
      <Button
        type="primary"
        onClick={() => {
          localStorage.setItem('reports_user', 'coordinator');
          window.location.reload();
        }}
      >
        Sign in as coordinator (mock)
      </Button>
    }
  />
);
