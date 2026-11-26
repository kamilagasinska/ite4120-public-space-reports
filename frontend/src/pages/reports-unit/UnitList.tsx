import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Select } from 'antd';
import { AppButtonPrimary, AppCard, AppLink, AppTag, AppTimestamp, ResourceList, useAppNotification, type ResourceListColumn } from '@helex/ui';
import { reportsApi, type Report, type Unit } from '../../reportsApi';
import { art } from '../reports/art';
import { catOf } from '../reports/store';
import { HANDOVER, StatusTag, signedUnit } from '../reports-coordinator/shared';

/**
 * The reports handed to one unit, the stand-in for the document system of the unit. In mock
 * auth the unit signs in as its inbox address.
 */
export const UnitList = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const inbox = signedUnit();
  const [rows, setRows] = useState<Report[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!inbox) return;
    reportsApi.handed().then(setRows).catch((e) => notify.error('Could not load reports', e.message)).finally(() => setLoading(false));
  }, [inbox, notify]);

  if (!inbox) return <SignIn />;

  const open = rows.filter((r) => r.status !== 'RESOLVED').length;
  const columns: ResourceListColumn<Report>[] = [
    {
      key: 'referenceNumber', title: 'Reference', dataIndex: 'referenceNumber', locked: true,
      render: (ref: string) => <AppLink style={{ whiteSpace: 'nowrap' }} href={`/unit/${ref}`} onClick={(e) => { e.preventDefault(); navigate(`/unit/${ref}`); }}>{ref}</AppLink>,
    },
    { key: 'status', title: 'Status', dataIndex: 'status', render: (s: Report['status']) => <StatusTag status={s} /> },
    { key: 'categoryName', title: 'Category', dataIndex: 'categoryName', render: (c: string, r) => <span className="work-cat"><span dangerouslySetInnerHTML={{ __html: art[catOf(r.categoryCode)] }} aria-hidden="true" />{c}</span> },
    { key: 'address', title: 'Address', dataIndex: 'address' },
    { key: 'handedAt', title: 'Handed to us', dataIndex: 'handedAt', render: (t: string) => <AppTimestamp value={t} /> },
    { key: 'handoverState', title: 'Letter', dataIndex: 'handoverState', render: (s: Report['handoverState']) => (s ? <AppTag color={HANDOVER[s].color}>{s === 'SENT' ? 'new' : 'opened'}</AppTag> : '') },
    { key: 'daysOpen', title: 'Days open', dataIndex: 'daysOpen' },
  ];
  return (
    <>
      <div className="work-hello"><h1>{rows[0]?.handoverUnit ?? 'Your unit'}</h1><p>{inbox}. {open ? `${open} open ${open === 1 ? 'report' : 'reports'}` : 'Nothing open'}, handed over X-Road by the coordinator.</p></div>
      <ResourceList<Report> embedded title="Handed to us" columns={columns} dataSource={rows} rowKey="referenceNumber" loading={loading} />
    </>
  );
};

/** Mock sign-in as one of the units that have open reports. */
const SignIn = () => {
  const [units, setUnits] = useState<Unit[]>([]);
  const [pick, setPick] = useState<string>();
  useEffect(() => { reportsApi.units().then(setUnits).catch(() => undefined); }, []);
  return (
    <AppCard title="Sign in as a unit (mock)" style={{ maxWidth: 560, margin: '48px auto' }}>
      <p>A unit solves the reports the coordinator hands to it. In this course the unit signs in as its inbox address, without a password.</p>
      <Select aria-label="Unit" placeholder="Choose a unit" style={{ width: '100%', marginBottom: 16 }} value={pick} onChange={setPick}
        options={units.map((u) => ({ value: u.inbox, label: `${u.recipient} (${u.reports} open)` }))} />
      <AppButtonPrimary disabled={!pick} onClick={() => { localStorage.setItem('reports_user', pick!); window.location.assign('/unit'); }}>Sign in as this unit</AppButtonPrimary>
    </AppCard>
  );
};
