import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Select, Space } from 'antd';
import { AppLink, AppTag, AppTimestamp, ResourceList, useAppNotification, type ResourceListColumn } from '@helex/ui';
import { ApiProblem, reportsApi, type Category, type Report, type ReportStatus } from '../../reportsApi';
import { art } from '../reports/art';
import { catOf } from '../reports/store';
import { CHANNEL, DOT, NoAccess, STATUS, StatusTag } from './shared';

const PAGE = 20;
const STATES = Object.keys(STATUS) as ReportStatus[];

/** The reporting operation of REPORTS.01. Without another sort the longest waiting report is first. */
export const ReportList = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [rows, setRows] = useState<Report[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryCode, setCategoryCode] = useState<string>();
  const [status, setStatus] = useState<ReportStatus>();
  const [channel, setChannel] = useState<Report['channel']>();
  const [sort, setSort] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [counts, setCounts] = useState<Partial<Record<ReportStatus, number>>>({});
  const call = useRef(0);

  useEffect(() => {
    reportsApi.categories().then(setCategories).catch(() => undefined);
  }, []);

  /* The counters follow the other filters, so a card shows what pressing it will give. */
  useEffect(() => {
    let live = true;
    Promise.all(STATES.map((s) => reportsApi.list({ categoryCode, channel, status: s, limit: 1 })))
      .then((all) => { if (live) setCounts(Object.fromEntries(all.map((r, i) => [STATES[i], r.meta?.total ?? r.data.length]))); })
      .catch(() => undefined);
    return () => { live = false; };
  }, [categoryCode, channel]);

  const load = useCallback(() => {
    const mine = ++call.current;
    setLoading(true);
    reportsApi
      .list({ categoryCode, status, channel, sort, limit: PAGE, offset: (page - 1) * PAGE })
      .then((r) => {
        if (mine !== call.current) return;
        setRows(r.data);
        setTotal(r.meta?.total ?? r.data.length);
      })
      .catch((e) => {
        if (e instanceof ApiProblem && e.status === 403) setDenied(true);
        else notify.error('Could not load reports', e.message);
      })
      .finally(() => mine === call.current && setLoading(false));
  }, [categoryCode, status, channel, sort, page, notify]);
  useEffect(load, [load]);

  if (denied) return <NoAccess />;

  const columns: ResourceListColumn<Report>[] = [
    {
      key: 'referenceNumber', title: 'Reference', dataIndex: 'referenceNumber', locked: true,
      render: (ref: string) => (
        <AppLink style={{ whiteSpace: 'nowrap' }} href={`/reports/${ref}`} onClick={(e) => { e.preventDefault(); navigate(`/reports/${ref}`); }}>{ref}</AppLink>
      ),
    },
    { key: 'status', title: 'Status', dataIndex: 'status', render: (s: ReportStatus) => <StatusTag status={s} /> },
    { key: 'categoryName', title: 'Category', dataIndex: 'categoryName', render: (c: string, r) => <span className="work-cat"><span dangerouslySetInnerHTML={{ __html: art[catOf(r.categoryCode)] }} aria-hidden="true" />{c}</span> },
    { key: 'recipientName', title: 'Recipient', dataIndex: 'recipientName' },
    {
      key: 'address', title: 'Address', dataIndex: 'address',
      render: (a: string, r) => <>{a} {!r.addressVerified && <AppTag color="orange">address not verified</AppTag>}</>,
    },
    { key: 'daysOpen', title: 'Days open', dataIndex: 'daysOpen' },
    {
      key: 'daysToRespond', title: 'Days to respond', dataIndex: 'daysToRespond',
      render: (d?: number) => (d === undefined ? 'answered' : d < 0 ? <AppTag color="red">late by {-d}</AppTag> : d),
    },
    { key: 'reminders', title: 'Reminders', dataIndex: 'reminders', defaultVisible: false, render: (n?: number) => n || '' },
    { key: 'seenBy', title: 'Seen by', dataIndex: 'seenBy', defaultVisible: false },
    { key: 'channel', title: 'Sent as', dataIndex: 'channel', render: (c: Report['channel']) => CHANNEL[c] },
    { key: 'responsibleUnit', title: 'Responsible unit', dataIndex: 'responsibleUnit', defaultVisible: false },
    { key: 'municipalityName', title: 'Municipality', dataIndex: 'municipalityName', defaultVisible: false },
    { key: 'submittedAt', title: 'Submitted', dataIndex: 'submittedAt', defaultVisible: false, render: (t: string) => <AppTimestamp value={t} /> },
  ];
  const first = <T,>(set: (v: T) => void) => (v: T) => { setPage(1); set(v); };

  const all = STATES.reduce((n, s) => n + (counts[s] ?? 0), 0);
  const pick = first(setStatus);
  const card = (text: string, n: number | undefined, s?: ReportStatus) => (
    <button key={text} type="button" aria-pressed={status === s} onClick={() => pick(s)}>
      <b>{n ?? '–'}</b><span>{s && <i style={{ background: DOT[s] }} />}{text}</span>
    </button>
  );

  return (
    <>
      <div className="work-hello"><h1>Reports of residents</h1><p>{counts.RECEIVED ? `${counts.RECEIVED} waiting for your first answer` : 'Nothing is waiting for a first answer'}</p></div>
      <div className="work-counts" role="group" aria-label="Status">
        {card('All', Object.keys(counts).length ? all : undefined)}
        {STATES.map((s) => card(STATUS[s].text, counts[s], s))}
      </div>
      <ResourceList<Report>
        embedded
        title={status ? STATUS[status].text : 'All reports'}
        columns={columns}
        dataSource={rows}
        rowKey="referenceNumber"
        loading={loading}
        pagination={{ current: page, pageSize: PAGE, total, onChange: setPage, showSizeChanger: false }}
        actions={
          <Space wrap>
            <Select aria-label="Category" placeholder="Category" allowClear style={{ width: 190 }} value={categoryCode} onChange={first(setCategoryCode)}
              options={categories.map((c) => ({ value: c.code, label: c.name }))} />
            <Select aria-label="Sent as" placeholder="Sent as" allowClear style={{ width: 120 }} value={channel} onChange={first(setChannel)}
              options={(Object.keys(CHANNEL) as Report['channel'][]).map((c) => ({ value: c, label: CHANNEL[c] }))} />
            <Select aria-label="Order" placeholder="Longest waiting first" allowClear style={{ width: 190 }} value={sort} onChange={first(setSort)}
              options={[{ value: '-submittedAt', label: 'Newest first' }, { value: 'status', label: 'By status' }, { value: 'categoryCode', label: 'By category' }]} />
          </Space>
        }
      />
    </>
  );
};
