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

/** The same changes the server allows. The server decides, this only hides the impossible ones. */
export const NEXT: Record<ReportStatus, ReportStatus[]> = {
  RECEIVED: ['ACCEPTED', 'IN_PROGRESS', 'REJECTED'],
  ACCEPTED: ['IN_PROGRESS', 'REJECTED'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: [],
  REJECTED: [],
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
