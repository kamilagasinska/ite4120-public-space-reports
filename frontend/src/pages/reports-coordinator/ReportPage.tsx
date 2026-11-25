import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Checkbox, DatePicker, Form, Image, Input, Select, Space, Timeline, Upload } from 'antd';
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
        view('categoryName', 'Category'), view('description', 'Description'),
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
      sidebar={
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          {report.status && <StatusCard key={`${report.status}-${version}`} report={report} act={act} />}
          <UpdateCard closed={!NEXT[report.status]?.length} act={act} referenceNumber={referenceNumber} />
          <ForwardCard closed={!NEXT[report.status]?.length} act={act} referenceNumber={referenceNumber} />
          <AppCard title="Photos" size="small">
            <Image.PreviewGroup>
              <Space wrap>{photos.map((p) => <figure key={p.id} style={{ margin: 0 }}><Image width={110} src={p.url} alt={p.kind === 'AFTER' ? 'Result' : 'Problem'} /><figcaption>{p.kind === 'AFTER' ? 'Result' : 'Problem'}</figcaption></figure>)}</Space>
            </Image.PreviewGroup>
            {!photos.length && <p>No photos.</p>}
            <Upload accept="image/jpeg,image/png" showUploadList={false} beforeUpload={(f) => { void act('Photo added', () => reportsApi.addPhoto(referenceNumber, f)); return false; }}>
              <AppButtonSecondary>Add a photo of the result</AppButtonSecondary>
            </Upload>
          </AppCard>
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

const StatusCard = ({ report, act }: { report: Report; act: Act }) => {
  const next = NEXT[report.status];
  const [to, setTo] = useState<ReportStatus>();
  const [reason, setReason] = useState('');
  const [falseReport, setFalseReport] = useState(false);
  const [publish, setPublish] = useState(false);
  const [busy, setBusy] = useState(false);
  const rejecting = to === 'REJECTED';
  if (!next.length) {
    return <AppCard title="Status" size="small"><StatusTag status={report.status} /> {report.rejectionReason}</AppCard>;
  }
  const save = () => {
    if (!to) return;
    setBusy(true);
    void act('Status changed', () => reportsApi.changeStatus(report.referenceNumber, {
      toStatus: to, reason: reason.trim() || undefined, falseReport: rejecting && falseReport, publish: !rejecting && publish,
    })).finally(() => setBusy(false));
  };
  return (
    <AppCard title="Change status" size="small">
      <Form layout="vertical" onFinish={save}>
        <Form.Item label="New status" required>
          <Select aria-label="New status" value={to} onChange={setTo} placeholder="Choose" options={next.map((s) => ({ value: s, label: STATUS[s].text }))} />
        </Form.Item>
        <Form.Item label={rejecting ? 'Reason, the resident sees it' : 'Reason, optional'} required={rejecting}>
          <Input.TextArea aria-label="Reason" maxLength={500} showCount value={reason} onChange={(e) => setReason(e.target.value)} />
        </Form.Item>
        {rejecting && <Form.Item><Checkbox checked={falseReport} onChange={(e) => setFalseReport(e.target.checked)}>False report, the sender loses the trust points</Checkbox></Form.Item>}
        {to && !rejecting && !report.published && <Form.Item><Checkbox checked={publish} onChange={(e) => setPublish(e.target.checked)}>Show text and photos on the public map. I checked that they name nobody.</Checkbox></Form.Item>}
        <AppButtonPrimary htmlType="submit" loading={busy} disabled={!to || (rejecting && !reason.trim())}>Save</AppButtonPrimary>
      </Form>
    </AppCard>
  );
};

const UpdateCard = ({ referenceNumber, closed, act }: { referenceNumber: string; closed: boolean; act: Act }) => {
  const [message, setMessage] = useState('');
  const [day, setDay] = useState<Dayjs | null>(null);
  if (closed) return null;
  return (
    <AppCard title="News for the resident" size="small">
      <Form layout="vertical" onFinish={() => act('Sent', () => reportsApi.update(referenceNumber, message, day?.format('YYYY-MM-DD'))).then((ok) => { if (ok) { setMessage(''); setDay(null); } })}>
        <Form.Item label="Text" required><Input.TextArea aria-label="Text" maxLength={500} showCount value={message} onChange={(e) => setMessage(e.target.value)} /></Form.Item>
        <Form.Item label="Expected on"><DatePicker aria-label="Expected on" value={day} onChange={setDay} style={{ width: '100%' }} /></Form.Item>
        <AppButtonSecondary htmlType="submit" disabled={!message.trim()}>Send</AppButtonSecondary>
      </Form>
    </AppCard>
  );
};

const ForwardCard = ({ referenceNumber, closed, act }: { referenceNumber: string; closed: boolean; act: Act }) => {
  const [kind, setKind] = useState<string>();
  const [name, setName] = useState('');
  const [reason, setReason] = useState('');
  if (closed) return null;
  const needsName = kind === 'MUNICIPALITY' || kind === 'OTHER';
  return (
    <AppCard title="Forward to another recipient" size="small">
      <Form layout="vertical" onFinish={() => act('Forwarded', () => reportsApi.forward(referenceNumber, { kind: kind!, name: needsName ? name : undefined, reason })).then((ok) => { if (ok) { setKind(undefined); setName(''); setReason(''); } })}>
        <Form.Item label="Recipient" required>
          <Select aria-label="Recipient" value={kind} onChange={setKind} placeholder="Choose" options={[
            { value: 'STATE_ROADS', label: 'Transport Administration' }, { value: 'MUNICIPALITY', label: 'A municipality' }, { value: 'OTHER', label: 'Somebody else' }]} />
        </Form.Item>
        {needsName && <Form.Item label="Name" required><Input aria-label="Name" maxLength={220} value={name} onChange={(e) => setName(e.target.value)} /></Form.Item>}
        <Form.Item label="Reason, the resident sees it" required><Input.TextArea aria-label="Reason for forwarding" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></Form.Item>
        <AppButtonSecondary htmlType="submit" disabled={!kind || !reason.trim() || (needsName && !name.trim())}>Forward</AppButtonSecondary>
      </Form>
    </AppCard>
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
