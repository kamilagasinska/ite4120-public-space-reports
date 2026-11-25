import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Checkbox, DatePicker, Form, Image, Input, Modal, Select, Space, Steps, Timeline, Upload } from 'antd';
import { FileTextOutlined } from '@ant-design/icons';
import { useDataController } from '@helex/core';
import {
  AppButtonPrimary, AppButtonSecondary, AppCard, AppTag, AppTimestamp, FieldItem, ResourceForm, useAppNotification,
  type ResourceFormSection,
} from '@helex/ui';
import type { Dayjs } from 'dayjs';
import {
  ApiProblem, reportsApi, type Forwarding, type Message, type PhotoInfo, type Report, type ReportStatus, type StatusChange,
} from '../../reportsApi';
import { art } from '../reports/art';
import { MapView } from '../reports/MapView';
import '../reports/hio.css';
import { catOf } from '../reports/store';
import { CHANNEL, NEXT, NoAccess, STATUS, StatusTag } from './shared';

type Photo = PhotoInfo & { url: string };
const text = (v: unknown) => (v === undefined || v === null || v === '' ? '—' : String(v));

/** One report for the coordinator, on ResourceForm in view mode. The fields of the resident are never edited. */
export const ReportPage = () => {
  const { referenceNumber = '' } = useParams();
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [form] = Form.useForm<Report>();
  const dc = useDataController<Report>({} as Report);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [history, setHistory] = useState<StatusChange[]>([]);
  const [messages, setMessages] = useState<Message[]>([]);
  const [forwardings, setForwardings] = useState<Forwarding[]>([]);
  const [photos, setPhotos] = useState<Photo[]>([]);
  const [version, setVersion] = useState(0);
  const reload = useCallback(() => setVersion((v) => v + 1), []);

  useEffect(() => {
    let live = true;
    const made: string[] = [];
    (async () => {
      try {
        const report = await reportsApi.one(referenceNumber);
        const [h, m, f, p] = await Promise.all([
          reportsApi.history(referenceNumber), reportsApi.messages(referenceNumber),
          reportsApi.forwardings(referenceNumber), reportsApi.photos(referenceNumber),
        ]);
        const withUrl = await Promise.all(p.map(async (x) => {
          const url = await reportsApi.photoUrl(referenceNumber, x.id);
          made.push(url);
          return { ...x, url };
        }));
        if (!live) return;
        dc.load(report);
        form.setFieldsValue(report);
        setHistory(h); setMessages(m); setForwardings(f); setPhotos(withUrl);
      } catch (e) {
        if (!live) return;
        if (e instanceof ApiProblem && e.status === 403) setDenied(true);
        else {
          notify.error('Could not load the report', (e as Error).message);
          navigate('/reports');
        }
      } finally {
        if (live) setLoading(false);
      }
    })();
    return () => { live = false; made.forEach((u) => URL.revokeObjectURL(u)); };
    // dc, form and navigate are stable handles; the record is keyed by the route parameter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [referenceNumber, version, notify]);

  if (denied) return <NoAccess />;
  const report = dc.current;

  const act = (done: string, action: () => Promise<unknown>) =>
    action().then(() => { notify.success(done, referenceNumber); reload(); return true; })
      .catch((e) => {
        notify.error('Not saved', (e as Error).message); // 400/409 problem detail, verbatim
        if (e instanceof ApiProblem && e.status === 409) reload();
        return false;
      });

  const view = (name: keyof Report & string, label: string, render?: (r: Report) => ReactNode) =>
    ({ name, label, type: 'text' as const, modeOverride: 'view' as const, viewRender: (v: unknown) => (render ? render(report) : text(v)) });
  const sections: ResourceFormSection<Report>[] = [
    {
      key: 'problem', title: 'Problem',
      fields: [
        view('categoryName', 'Category'),
        view('address', 'Address', (r) => <>{text(r.address)} {r.addressVerified ? <AppTag color="green">verified</AppTag> : <AppTag color="orange">address not verified</AppTag>}</>),
        view('latitude', 'Map point', (r) => (r.latitude === undefined ? '—' : `${r.latitude}, ${r.longitude}`)),
        view('municipalityName', 'Municipality'),
      ],
    },
    {
      key: 'people', title: 'Recipient and sender',
      fields: [
        view('recipientName', 'Recipient'), view('responsibleUnit', 'Responsible unit'),
        view('channel', 'Sent as', (r) => (r.channel ? CHANNEL[r.channel] : '—')), view('reporterContact', 'Contact'),
        view('daysToRespond', 'Days to respond', (r) => (r.daysToRespond === undefined ? 'answered' : r.daysToRespond < 0 ? `late by ${-r.daysToRespond}` : r.daysToRespond)),
        view('reminders', 'Reminders'), view('seenBy', 'Seen by'),
        view('senderPoints', 'Trust of the sender', trust),
      ],
    },
  ];

  return (
    <ResourceForm<Report>
      mode="view"
      entityLabel="Report"
      icon={<FileTextOutlined />}
      name={report.referenceNumber}
      subtitle={report.categoryName}
      chips={<Space><StatusTag status={report.status} />{report.published && <AppTag color="green">on the public map</AppTag>}</Space>}
      loading={loading}
      form={form}
      dataController={dc}
      initialValues={report}
      sections={sections}
      onBack={() => navigate('/reports')}
      backTooltip="Back to the list"
      banner={report.status && <Case key={`${report.status}-${version}`} report={report} photos={photos} act={act} />}
      sidebar={
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <AppCard title="What happened" size="small">
            <Timeline items={events(report, history, messages, forwardings)} />
          </AppCard>
          <AppCard title="Metadata" size="small">
            <FieldItem label="Created at"><AppTimestamp value={report.sysCreatedAt} fallback="—" /></FieldItem>
            <FieldItem label="Created by">{text(report.sysCreatedBy)}</FieldItem>
            <FieldItem label="Modified at"><AppTimestamp value={report.sysModifiedAt} fallback="—" /></FieldItem>
            <FieldItem label="Modified by">{text(report.sysModifiedBy)}</FieldItem>
            <FieldItem label="Version">{text(report.sysVersion)}</FieldItem>
          </AppCard>
        </Space>
      }
    />
  );
};

type Act = (done: string, action: () => Promise<unknown>) => Promise<boolean>;

const STEPS: ReportStatus[] = ['RECEIVED', 'ACCEPTED', 'IN_PROGRESS', 'RESOLVED'];
const DO: Record<ReportStatus, string> = { RECEIVED: '', ACCEPTED: 'Accept', IN_PROGRESS: 'Start work', RESOLVED: 'Mark as resolved', REJECTED: 'Reject' };
type Open = ReportStatus | 'news' | 'forward' | null;

/** What the worker needs first: the words of the resident, the photos, the place and what can be done. */
const Case = ({ report, photos, act }: { report: Report; photos: Photo[]; act: Act }) => {
  const [open, setOpen] = useState<Open>(null);
  const next = NEXT[report.status];
  const ref = report.referenceNumber;
  const close = () => setOpen(null);
  const spot = report.latitude !== undefined && report.longitude !== undefined ? { latitude: report.latitude, longitude: report.longitude } : undefined;
  const done = (ok: boolean) => { if (ok) close(); };
  return (
    <div className="work-case">
      <section className="work-box work-steps" aria-label="Progress">
        {report.status === 'REJECTED'
          ? <div><StatusTag status={report.status} /> {report.rejectionReason}</div>
          : <Steps current={STEPS.indexOf(report.status)} status={report.status === 'RESOLVED' ? 'finish' : 'process'} items={STEPS.map((s) => ({ title: STATUS[s].text }))} />}
        <div className="work-acts">
          {next.filter((s) => s !== 'REJECTED').map((s, i) => (i === 0
            ? <AppButtonPrimary key={s} size="large" onClick={() => setOpen(s)}>{DO[s]}</AppButtonPrimary>
            : <AppButtonSecondary key={s} size="large" onClick={() => setOpen(s)}>{DO[s]}</AppButtonSecondary>))}
          {next.length > 0 && <AppButtonSecondary size="large" onClick={() => setOpen('news')}>Write news</AppButtonSecondary>}
          {next.length > 0 && <AppButtonSecondary size="large" onClick={() => setOpen('forward')}>Forward</AppButtonSecondary>}
          {next.includes('REJECTED') && <AppButtonSecondary size="large" danger onClick={() => setOpen('REJECTED')}>{DO.REJECTED}</AppButtonSecondary>}
          {next.length === 0 && <em>This report is closed. {report.status === 'RESOLVED' && 'The resident can say that the problem remains, then it opens again.'}</em>}
        </div>
      </section>
      <section className="work-box" aria-label="Problem">
        <div className="work-head">
          <span dangerouslySetInnerHTML={{ __html: art[catOf(report.categoryCode)] }} aria-hidden="true" />
          <div><h2>{report.address}</h2><Space wrap>{report.addressVerified ? <AppTag color="green">address verified</AppTag> : <AppTag color="orange">address not verified</AppTag>}{deadline(report)}{(report.seenBy ?? 1) > 1 && <AppTag color="blue">seen by {report.seenBy}</AppTag>}{!!report.reminders && <AppTag color="red">reminded {report.reminders}×</AppTag>}{!!report.senderFalseReports && <AppTag color="red">sender had a false report</AppTag>}</Space></div>
        </div>
        <blockquote>{report.description}</blockquote>
        <Image.PreviewGroup>
          <ul className="work-photos">{photos.map((p) => <li key={p.id}><figure><Image width={150} height={120} src={p.url} alt={p.kind === 'AFTER' ? 'Result' : 'Problem'} /><figcaption>{p.kind === 'AFTER' ? 'Result' : 'Problem'}</figcaption></figure></li>)}</ul>
        </Image.PreviewGroup>
        {!photos.length && <p>No photos.</p>}
        <Upload accept="image/jpeg,image/png" showUploadList={false} beforeUpload={(f) => { void act('Photo added', () => reportsApi.addPhoto(ref, f)); return false; }}>
          <AppButtonSecondary style={{ marginTop: 12 }}>Add a photo of the result</AppButtonSecondary>
        </Upload>
      </section>
      <section className="work-map" aria-label="Place">
        {spot ? <div className="hio"><MapView center={spot} zoom={16} pin={spot} label={() => ''} /></div>
          : <div className="work-none">The resident typed the address and did not put a pin on the map.</div>}
      </section>
      <Modal open={open !== null} onCancel={close} footer={null} destroyOnHidden
        title={open === 'news' ? 'News for the resident' : open === 'forward' ? 'Forward to another recipient' : open ? `${DO[open]}, ${ref}` : ''}>
        {open === 'news' ? <UpdateForm referenceNumber={ref} act={act} onDone={done} />
          : open === 'forward' ? <ForwardForm referenceNumber={ref} act={act} onDone={done} />
            : open && <StatusForm report={report} to={open} act={act} onDone={done} />}
      </Modal>
    </div>
  );
};

/** How far the sender can be trusted. Who it is stays unknown. */
const trust = (r: Report) => (r.senderReports === undefined ? 'A guest, no history'
  : `${r.senderPoints} points, ${r.senderReports} ${r.senderReports === 1 ? 'report' : 'reports'}, ${r.senderFalseReports} false`);

const deadline = (r: Report) => {
  if (r.daysToRespond === undefined) return <AppTag color="green">answered</AppTag>;
  return r.daysToRespond < 0 ? <AppTag color="red">late by {-r.daysToRespond} days</AppTag> : <AppTag color="blue">{r.daysToRespond} days to answer</AppTag>;
};

type Done = { act: Act; onDone: (ok: boolean) => void };

const StatusForm = ({ report, to, act, onDone }: Done & { report: Report; to: ReportStatus }) => {
  const [reason, setReason] = useState('');
  const [falseReport, setFalseReport] = useState(false);
  const [publish, setPublish] = useState(false);
  const [busy, setBusy] = useState(false);
  const rejecting = to === 'REJECTED';
  const save = () => {
    setBusy(true);
    void act('Status changed', () => reportsApi.changeStatus(report.referenceNumber, {
      toStatus: to, reason: reason.trim() || undefined, falseReport: rejecting && falseReport, publish: !rejecting && publish,
    })).then(onDone).finally(() => setBusy(false));
  };
  return (
    <Form layout="vertical" onFinish={save}>
      <Form.Item label={rejecting ? 'Reason, the resident sees it' : 'Reason, optional'} required={rejecting}>
        <Input.TextArea aria-label="Reason" autoSize={{ minRows: 3 }} maxLength={500} showCount value={reason} onChange={(e) => setReason(e.target.value)} />
      </Form.Item>
      {rejecting && <Form.Item><Checkbox checked={falseReport} onChange={(e) => setFalseReport(e.target.checked)}>False report, the sender loses the trust points</Checkbox></Form.Item>}
      {!rejecting && !report.published && <Form.Item><Checkbox checked={publish} onChange={(e) => setPublish(e.target.checked)}>Show text and photos on the public map. I checked that they name nobody.</Checkbox></Form.Item>}
      <AppButtonPrimary htmlType="submit" loading={busy} disabled={rejecting && !reason.trim()}>{DO[to]}</AppButtonPrimary>
    </Form>
  );
};

const UpdateForm = ({ referenceNumber, act, onDone }: Done & { referenceNumber: string }) => {
  const [message, setMessage] = useState('');
  const [day, setDay] = useState<Dayjs | null>(null);
  return (
    <Form layout="vertical" onFinish={() => act('Sent', () => reportsApi.update(referenceNumber, message, day?.format('YYYY-MM-DD'))).then(onDone)}>
      <Form.Item label="Text" required><Input.TextArea aria-label="Text" autoSize={{ minRows: 3 }} maxLength={500} showCount value={message} onChange={(e) => setMessage(e.target.value)} /></Form.Item>
      <Form.Item label="Expected on"><DatePicker aria-label="Expected on" value={day} onChange={setDay} style={{ width: '100%' }} /></Form.Item>
      <AppButtonPrimary htmlType="submit" disabled={!message.trim()}>Send</AppButtonPrimary>
    </Form>
  );
};

const ForwardForm = ({ referenceNumber, act, onDone }: Done & { referenceNumber: string }) => {
  const [kind, setKind] = useState<string>();
  const [name, setName] = useState('');
  const [reason, setReason] = useState('');
  const needsName = kind === 'MUNICIPALITY' || kind === 'OTHER';
  return (
    <Form layout="vertical" onFinish={() => act('Forwarded', () => reportsApi.forward(referenceNumber, { kind: kind!, name: needsName ? name : undefined, reason })).then(onDone)}>
      <Form.Item label="Recipient" required>
        <Select aria-label="Recipient" value={kind} onChange={setKind} placeholder="Choose" options={[
          { value: 'STATE_ROADS', label: 'Transport Administration' }, { value: 'MUNICIPALITY', label: 'A municipality' }, { value: 'OTHER', label: 'Somebody else' }]} />
      </Form.Item>
      {needsName && <Form.Item label="Name" required><Input aria-label="Name" maxLength={220} value={name} onChange={(e) => setName(e.target.value)} /></Form.Item>}
      <Form.Item label="Reason, the resident sees it" required><Input.TextArea aria-label="Reason for forwarding" autoSize={{ minRows: 3 }} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></Form.Item>
      <AppButtonPrimary htmlType="submit" disabled={!kind || !reason.trim() || (needsName && !name.trim())}>Forward</AppButtonPrimary>
    </Form>
  );
};

/** Everything that happened, newest first. */
function events(report: Report, history: StatusChange[], messages: Message[], forwardings: Forwarding[]) {
  const all = [
    ...history.map((h) => ({ at: h.sysCreatedAt, by: h.sysCreatedBy, what: `${STATUS[h.fromStatus].text} → ${STATUS[h.toStatus].text}${h.falseReport ? ', false report' : ''}`, why: h.reason })),
    ...messages.map((m) => ({ at: m.sysCreatedAt, by: m.sysCreatedBy, what: { UPDATE: 'News for the resident', REMINDER: 'Reminder of the resident', REMAINS: 'The resident says the problem remains' }[m.kind], why: [m.text, m.expectedOn && `expected on ${m.expectedOn}`].filter(Boolean).join(', ') })),
    ...forwardings.map((f) => ({ at: f.sysCreatedAt, by: f.sysCreatedBy, what: `${f.fromName} → ${f.toName}`, why: f.reason })),
    ...(report.submittedAt ? [{ at: report.submittedAt, by: undefined, what: 'Received', why: undefined }] : []),
  ].sort((a, b) => Date.parse(b.at) - Date.parse(a.at));
  return all.map((e, i) => ({
    key: i,
    content: <><b>{e.what}</b>{e.why && <div>{e.why}</div>}<div><AppTimestamp value={e.at} />{e.by ? `, ${e.by}` : ''}</div></>,
  }));
}
