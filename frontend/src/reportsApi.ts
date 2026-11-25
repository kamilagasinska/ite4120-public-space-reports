// The public space reports API client (REPORTS.01). Shared by every reports page.

export type ReportStatus = 'RECEIVED' | 'ACCEPTED' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED';

export interface Report {
  referenceNumber: string;
  categoryCode: string;
  categoryName: string;
  responsibleUnit: string;
  address: string;
  addressVerified: boolean;
  latitude?: number;
  longitude?: number;
  municipalityCode?: string;
  municipalityName?: string;
  recipientKind: string;
  recipientName: string;
  channel: 'GUEST' | 'DEVICE' | 'SIGNED';
  status: ReportStatus;
  published?: boolean;
  rejectionReason?: string;
  submittedAt: string;
  daysOpen: number;
  respondBy?: string;
  daysToRespond?: number;
  /** The rest is returned only to a coordinator. */
  description?: string;
  reporterContact?: string;
  reminders?: number;
  seenBy?: number;
  /** Only for one report. A guest has none. */
  senderReports?: number;
  senderFalseReports?: number;
  senderPoints?: number;
  sysCreatedAt?: string;
  sysCreatedBy?: string;
  sysModifiedAt?: string;
  sysModifiedBy?: string;
  sysVersion?: number;
}

export interface Category { code: string; name: string; responsibleUnit: string; onMap: boolean }
export interface StatusChange { fromStatus: ReportStatus; toStatus: ReportStatus; reason?: string; falseReport: boolean; sysCreatedAt: string; sysCreatedBy: string }
export interface Forwarding { fromName: string; toKind: string; toName: string; reason: string; sysCreatedAt: string; sysCreatedBy?: string }
export interface Message { kind: 'UPDATE' | 'REMINDER' | 'REMAINS'; text?: string; expectedOn?: string; sysCreatedAt: string; sysCreatedBy?: string }
export interface PhotoInfo { id: number; kind: 'BEFORE' | 'AFTER' }
export interface Place { fullAddress: string; municipalityCode?: string; municipalityName?: string; recipientKind?: string; recipientName?: string }
export interface Standing { points: number; level: number; nextLevelAt?: number }
export interface MapPoint { id: number; latitude: number; longitude: number; categoryCode: string; status: ReportStatus; municipalityName?: string; submittedOn: string; seenBy: number }
export interface MapDetail extends MapPoint { recipientName: string; address?: string; description?: string; photos: PhotoInfo[] }
export interface SigningState { state: 'PENDING' | 'SIGNED' | 'REFUSED' | 'EXPIRED' | 'FAILED'; referenceNumber?: string; submissionKey?: string; standing?: Standing; name?: string }
export interface NewReport { categoryCode: string; address: string; latitude?: number; longitude?: number; description: string; reporterContact?: string; submissionKey: string }

/** The problem document of the backend. `detail` is meant for people. */
export class ApiProblem extends Error {
  readonly status: number;
  readonly retryAfter?: number;

  constructor(status: number, detail: string, retryAfter?: number) {
    super(detail);
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

const BASE = '/api/reports';

async function send(path: string, init: RequestInit = {}): Promise<Response> {
  /* The template resets its mock user on every load, so these pages keep an own one. */
  const user = window.location.pathname.startsWith('/spot') ? 'resident' : localStorage.getItem('reports_user') || 'resident';
  const response = await fetch(BASE + path, {
    ...init,
    headers: { Authorization: `Bearer ${user}`, ...(init.headers as Record<string, string> | undefined) },
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    const retry = Number(response.headers.get('Retry-After'));
    throw new ApiProblem(response.status, problem?.detail || `HTTP ${response.status}`, retry || undefined);
  }
  return response;
}

const get = <T>(path: string, headers?: Record<string, string>): Promise<T> =>
  send(path, { headers }).then((r) => r.json());

const post = (path: string, body?: unknown, headers?: Record<string, string>): Promise<Response> =>
  send(path, {
    method: 'POST',
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

const ref = encodeURIComponent;
const sender = (key?: string): Record<string, string> => (key ? { 'X-Submission-Key': key } : {});

export const reportsApi = {
  categories: () => get<Category[]>('/categories'),
  place: (latitude: number, longitude: number, categoryCode?: string) =>
    get<Place>(`/place?latitude=${latitude}&longitude=${longitude}${categoryCode ? `&categoryCode=${categoryCode}` : ''}`),

  /** Without a device identifier it is a guest report. */
  submit: (report: NewReport, deviceId?: string): Promise<Report> =>
    post('', report, deviceId ? { 'X-Device-Id': deviceId } : {}).then((r) => r.json()),
  testPersons: () => get<{ document: string; label: string }[]>('/signings/persons'),
  startSigning: (report: NewReport, person: string): Promise<{ token: string; verificationCode: string }> =>
    post('/signings', { ...report, submissionKey: undefined, person }).then((r) => r.json()),
  signing: (token: string) => get<SigningState>(`/signings/${ref(token)}`),
  standing: (deviceId: string) => get<Standing>('/me', { 'X-Device-Id': deviceId }),

  one: (referenceNumber: string) => get<Report>(`/${ref(referenceNumber)}`),
  messages: (referenceNumber: string) => get<Message[]>(`/${ref(referenceNumber)}/messages`),
  forwardings: (referenceNumber: string) => get<Forwarding[]>(`/${ref(referenceNumber)}/forwardings`),
  remind: (referenceNumber: string, key: string) => post(`/${ref(referenceNumber)}/reminders`, undefined, sender(key)),
  remains: (referenceNumber: string, key: string, text: string): Promise<Report> =>
    post(`/${ref(referenceNumber)}/remains`, { text }, sender(key)).then((r) => r.json()),

  photos: (referenceNumber: string, key?: string) => get<PhotoInfo[]>(`/${ref(referenceNumber)}/photos`, sender(key)),
  addPhoto: (referenceNumber: string, file: File, key?: string): Promise<PhotoInfo> => {
    const form = new FormData();
    form.append('file', file);
    return send(`/${ref(referenceNumber)}/photos`, { method: 'POST', headers: sender(key), body: form }).then((r) => r.json());
  },
  /** An object URL, because an <img> cannot send the Authorization header. Revoke it after use. */
  photoUrl: (referenceNumber: string, id: number, key?: string): Promise<string> =>
    send(`/${ref(referenceNumber)}/photos/${id}`, { headers: sender(key) }).then((r) => r.blob()).then((b) => URL.createObjectURL(b)),

  map: (west: number, south: number, east: number, north: number, categoryCode?: string) =>
    get<MapPoint[]>(`/map?west=${west}&south=${south}&east=${east}&north=${north}${categoryCode ? `&categoryCode=${categoryCode}` : ''}`),
  sight: (id: number, deviceId: string): Promise<MapPoint> => post(`/map/${id}/sightings`, undefined, { 'X-Device-Id': deviceId }).then((r) => r.json()),
  mapDetail: (id: number) => get<MapDetail>(`/map/${id}`),
  mapPhotoUrl: (id: number, photoId: number): Promise<string> =>
    send(`/map/${id}/photos/${photoId}`).then((r) => r.blob()).then((b) => URL.createObjectURL(b)),

  /** Coordinator. */
  list: (query: Record<string, string | number | undefined>): Promise<{ data: Report[]; meta?: { total?: number } }> => {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => v !== undefined && v !== '' && params.set(k, String(v)));
    return get(`?${params}`);
  },
  changeStatus: (referenceNumber: string, body: { toStatus: ReportStatus; reason?: string; falseReport?: boolean; publish?: boolean }): Promise<Report> =>
    post(`/${ref(referenceNumber)}/status`, body).then((r) => r.json()),
  history: (referenceNumber: string) => get<StatusChange[]>(`/${ref(referenceNumber)}/history`),
  update: (referenceNumber: string, text: string, expectedOn?: string) => post(`/${ref(referenceNumber)}/updates`, { text, expectedOn }),
  forward: (referenceNumber: string, body: { kind: string; name?: string; reason: string }): Promise<Report> =>
    post(`/${ref(referenceNumber)}/forward`, body).then((r) => r.json()),
  xroadStatus: () => get<{ available: boolean; securityServer: string; service: string; messageId?: string; detail?: string }>('/xroad-status'),
};
