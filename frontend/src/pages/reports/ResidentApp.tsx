import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Input, Switch, Upload } from 'antd';
import {
  ApiProblem, reportsApi, type Forwarding, type MapDetail, type MapPoint, type Message, type NewReport,
  type PhotoInfo, type Report, type Standing,
} from '../../reportsApi';
import { art, avatar, icons, logo, medals } from './art';
import './hio.css';
import { MapView, SIGN, type Box, type Spot } from './MapView';
import {
  ANSWERS, CATS, catOf, codeOf, descriptionOf, detailsOf, ELSE, memory, NAME, newDraft, QUESTION, SIZES, TILE,
  type Cat, type Draft, type Person, type SentReport, type Way,
} from './store';
import { LANGS, translate, type Lang } from './texts';

const TALLINN: Spot = { latitude: 59.437, longitude: 24.7536 };
const STEP: Record<Report['status'], string> = {
  RECEIVED: 'Reported', ACCEPTED: 'Accepted', IN_PROGRESS: 'In progress', RESOLVED: 'Done', REJECTED: 'Rejected',
};

/** Own drawings from art.ts, never text of a user. */
const Svg = ({ code, className }: { code: string; className?: string }) => (
  <span className={className} style={{ display: 'contents' }} dangerouslySetInnerHTML={{ __html: code }} />
);
const Icon = ({ name }: { name: string }) => <Svg code={(icons as Record<string, string>)[name] ?? ''} />;

const message = (e: unknown, t: (k: string) => string) =>
  e instanceof ApiProblem && e.status < 500 ? e.message : t('Something went wrong. Try again.');

export const ResidentApp = () => {
  const { screen = memory.way() ? 'home' : 'welcome', id } = useParams();
  const navigate = useNavigate();
  const [lang, setLang] = useState<Lang>(memory.lang());
  const [way, setWay] = useState<Way | null>(memory.way());
  const [person, setPerson] = useState<Person | null>(memory.person());
  const [standing, setStanding] = useState<Standing | null>(memory.standing());
  const [sent, setSent] = useState<SentReport[]>(memory.sent());
  const [draft, setDraft] = useState<Draft>(newDraft('road'));
  const [editing, setEditing] = useState(false);
  const [photoNote, setPhotoNote] = useState('');
  const [signing, setSigning] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const started = useRef(false);

  const t = useCallback((key: string, ...n: number[]) => translate(lang, key, ...n), [lang]);
  const go = useCallback((s: string) => navigate(`/spot/${s}`), [navigate]);
  const change = (part: Partial<Draft>) => setDraft((d) => ({ ...d, ...part }));
  const signed = way === 'signed' && !draft.anonymous;

  useEffect(() => {
    document.documentElement.lang = lang;
    document.title = 'Hole in One';
  }, [lang]);
  useEffect(() => {
    window.scrollTo(0, 0);
    root.current?.querySelector('h1')?.focus();
  }, [screen, id]);
  /* A draft lives in memory, so after a reload the steps begin again. */
  const needsDraft = ['where', 'near', 'what', 'words', 'photo', 'contact', 'check', 'sign'].includes(screen);
  useEffect(() => {
    if (needsDraft && !started.current) navigate('/spot/home', { replace: true });
  }, [needsDraft, navigate]);

  const refreshStanding = useCallback(() => {
    const last = memory.sent().find((s) => s.signed && s.key.startsWith('signing-'));
    const ask = way === 'device' ? reportsApi.standing(memory.device())
      : way === 'signed' && last ? reportsApi.signing(last.key.slice('signing-'.length)).then((s) => s.standing ?? null)
        : Promise.resolve(null);
    ask.then((s) => { setStanding(s); memory.setStanding(s); }).catch(() => undefined);
  }, [way]);
  useEffect(() => { if (screen === 'home' || screen === 'account') refreshStanding(); }, [screen, refreshStanding]);

  const choose = (w: Way, p: Person | null = null) => {
    if (w === 'guest') memory.forgetDevice();
    memory.setWay(w); setWay(w);
    memory.setPerson(p); setPerson(p);
    memory.setStanding(null); setStanding(null);
    if (started.current) { change({ anonymous: false }); go('check'); } else go('home');
  };
  const remember = (list: SentReport[]) => { memory.setSent(list); setSent(list); };

  const request = (d: Draft): NewReport => ({
    categoryCode: codeOf(d), address: d.address, latitude: d.spot?.latitude, longitude: d.spot?.longitude,
    description: descriptionOf(d), reporterContact: d.contactWay === 'no' ? undefined : d.contact || undefined, submissionKey: d.key,
  });

  /** After the report is stored, the photos follow one by one. A photo that fails does not lose the report. */
  const finish = async (ref: string, key: string, isSigned: boolean) => {
    const photos = draft.photos;
    let failed = 0;
    for (const f of photos) {
      try { await reportsApi.addPhoto(ref, f, key); } catch { failed += 1; }
    }
    setPhotoNote(failed ? t('# of # photos could not be added.', failed, photos.length) : '');
    remember([{ ref, key, cat: draft.cat, address: draft.address, at: new Date().toISOString(), signed: isSigned, photo: photos.length > failed },
      ...sent.filter((s) => s.ref !== ref)]);
    started.current = false;
    setEditing(false);
    navigate(`/spot/done/${ref}`, { replace: true });
  };

  // ---- parts ------------------------------------------------------------
  const H = ({ children }: { children: ReactNode }) => <h1 tabIndex={-1}>{children}</h1>;
  const Back = ({ to, step }: { to: string; step?: number }) => (
    <div className="top">
      <button className="rb" aria-label={t('Back')} onClick={() => go(editing && step && step < 5 ? 'check' : to)}><Icon name="back" /></button>
      {step ? <div className="road" role="img" aria-label={t('Step # of #', step, 5)}><i style={{ width: `${step * 20}%` }} /></div> : null}
    </div>
  );
  const Go = ({ children, onClick, disabled, plum }: { children: ReactNode; onClick: () => void; disabled?: boolean; plum?: boolean }) => (
    <button className={plum ? 'go pl' : 'go'} disabled={disabled} onClick={onClick}>{children}</button>
  );
  const Txt = ({ children, onClick }: { children: ReactNode; onClick: () => void }) => <button className="txt" onClick={onClick}>{children}</button>;
  const LangButton = () => <button className="lng" onClick={() => go('lang')}><Icon name="globe" />{LANGS[lang]}</button>;
  const Thumb = ({ cat, size = 84 }: { cat: Cat; size?: number }) => <div className="thumb" style={{ width: size, height: size }}><Svg code={art[cat]} /></div>;
  const Tabs = ({ at }: { at: number }) => (
    <nav className="tabs" aria-label={t('Main')}>
      {([['home', 'plus2', 'Report'], ['map', 'map', 'Map'], ['mine', 'list', 'My reports'], ['account', 'mask', 'Profile']] as const).map(([s, icon, text], k) => (
        <button key={s} aria-current={k === at ? 'page' : undefined} onClick={() => go(s)}><Icon name={icon} />{t(text)}</button>
      ))}
    </nav>
  );
  const next = (s: string) => go(editing ? 'check' : s);

  // ---- screens ----------------------------------------------------------
  const ui = { t, go, next, Back, H, Go, Txt, Tabs, Thumb };
  let body: ReactNode;
  switch (screen) {
    case 'welcome':
      body = (
        <>
          <div className="homebar" style={{ margin: 0 }}><div className="brand"><Svg code={logo} /><span>Hole in One</span></div><LangButton /></div>
          <div className="welc"><Svg code={art.hero} /></div>
          <H>{t('Spot it. Report it. Done.')}</H>
          <p className="sub">{t('Sign in once and stay signed in on this phone.')}</p>
          <div className="foot" style={{ marginTop: 22 }}>
            <Go onClick={() => go('login')}>{t('Sign in')}</Go>
            <Go plum onClick={() => go('privacy')}>{t('Continue without signing in')}</Go>
          </div>
        </>
      );
      break;
    case 'privacy':
      body = (
        <>
          <Back to="welcome" />
          <H>{t('Before you start')}</H>
          <p className="sub">{t('We keep a random number of this device, not your name or phone number. It stops spam and collects your points. It is deleted after 6 months without a report.')}</p>
          <button className="way" onClick={() => choose('device')}><span className="ic"><Icon name="hash" /></span><span><b>{t('Use a device number')}</b><span className="lab">{t('Up to 5 reports a day at the start')}</span></span></button>
          <button className="way" onClick={() => choose('guest')}><span className="ic"><Icon name="mask" /></span><span><b>{t('Stay a guest')}</b><span className="lab">{t('1 report a day, nothing is kept on the device')}</span></span></button>
          <div className="grow" /><div className="foot"><Txt onClick={() => go('login')}>{t('Sign in with Smart-ID')}</Txt></div>
        </>
      );
      break;
    case 'login':
      body = <Login {...ui} onChoose={(p) => choose('signed', p)} back={started.current ? 'check' : 'welcome'} />;
      break;
    case 'lang':
      body = (
        <>
          <Back to={way ? 'home' : 'welcome'} /><H>{t('Language')}</H>
          <div style={{ marginTop: 8 }}>
            {(Object.keys(LANGS) as Lang[]).map((l) => (
              <button key={l} className="way" style={{ minHeight: 72 }} lang={l} aria-pressed={l === lang} onClick={() => { memory.setLang(l); setLang(l); }}>
                <span className="ic"><Icon name={l === lang ? 'ok' : 'globe'} /></span><b>{LANGS[l]}</b>
              </button>
            ))}
          </div>
          <p className="sub">{t('Ready answers are sent in English, your own words stay as you wrote them.')}</p>
        </>
      );
      break;
    case 'where':
      body = <Where {...ui} draft={draft} change={change} onYes={(near) => (near && !editing ? go('near') : next('what'))} back="home" />;
      break;
    case 'near':
      body = <Near {...ui} draft={draft} way={way} />;
      break;
    case 'seen':
      body = (
        <>
          <div className="load"><div style={{ width: 90, color: 'var(--mag)' }}><Icon name="eye" /></div><H>{t('Thanks, we counted you')}</H>
            <p className="sub">{t('The city sees that more people noticed it. You do not need to send it again.')}</p></div>
          <div className="foot"><Go plum onClick={() => go('home')}>{t('Back to start')}</Go></div>
        </>
      );
      break;
    case 'what': {
      const own = draft.what === ELSE;
      const ready = draft.size && (own ? draft.other.trim().length >= 10 : draft.what);
      body = (
        <>
          <Back to="where" step={2} /><H>{t(QUESTION[draft.cat])}</H>
          <div className="chips">
            {!own && ANSWERS[draft.cat].map(([a, icon]) => <button key={a} className="chip" aria-pressed={draft.what === a} onClick={() => change({ what: a })}><Icon name={icon} />{t(a)}</button>)}
            <button className="chip wide" aria-pressed={own} onClick={() => change({ what: ELSE })}><Icon name="dots" />{t(ELSE)}</button>
          </div>
          {own && (
            <>
              <p style={{ margin: '14px 0 8px' }}><label htmlFor="other"><b>{t('What do you see?')}</b></label></p>
              <Input.TextArea id="other" className="inp" maxLength={400} autoSize={{ minRows: 4 }} value={draft.other} onChange={(e) => change({ other: e.target.value })} />
              <p className="lab" style={{ marginTop: 6 }}>{t('At least 10 characters')}</p>
              <Txt onClick={() => change({ what: '' })}>{t('Show the list again')}</Txt>
            </>
          )}
          <h2 id="size">{t('How bad is it?')}</h2>
          <div className="three" role="group" aria-labelledby="size">
            {SIZES[draft.cat].map((s) => <button key={s} aria-pressed={draft.size === s} onClick={() => change({ size: s })}>{t(s)}</button>)}
          </div>
          {(draft.size === 'Dangerous' || draft.what === 'Dangerous object') && (
            <div className="note sos" role="status"><span>{t('Is someone in danger right now?')}</span><a className="sos112" href="tel:112"><Icon name="call" />{t('Call 112')}</a></div>
          )}
          <div className="grow" />
          <div className="foot">
            <Go disabled={!ready} onClick={() => next('photo')}>{t('Continue')}</Go>
            {!own && <Txt onClick={() => go('words')}>{t(draft.words ? 'Change my words' : 'Add my own words')}</Txt>}
          </div>
        </>
      );
      break;
    }
    case 'words':
      body = <Words {...ui} draft={draft} change={change} />;
      break;
    case 'photo':
      body = <Photos {...ui} draft={draft} change={change} />;
      break;
    case 'contact':
      body = <Contact {...ui} draft={draft} change={change} anonymous={!signed} onNext={() => { setEditing(true); go('check'); }} />;
      break;
    case 'check':
      body = (
        <Check {...ui} draft={draft} change={change} signed={signed} way={way} signing={signing}
          send={async () => {
            if (signed) { setSigning(true); return; }
            const report = await reportsApi.submit(request(draft), way === 'guest' ? undefined : memory.device());
            await finish(report.referenceNumber, draft.key, false);
          }}
        >
          {signing && person && (
            <SignSheet {...ui} report={request(draft)} person={person}
              onSigned={(ref, key, name, s) => {
                setSigning(false);
                if (name) { const p = { ...person, name }; memory.setPerson(p); setPerson(p); }
                if (s) { memory.setStanding(s); setStanding(s); }
                void finish(ref, key, true);
              }}
              onAnonymous={() => { setSigning(false); change({ anonymous: true }); }}
              onClose={() => setSigning(false)}
            />
          )}
        </Check>
      );
      break;
    case 'done': {
      const s = sent.find((x) => x.ref === id);
      body = (
        <>
          <svg className="stamp" viewBox="0 0 170 170" aria-hidden="true"><circle cx="85" cy="85" r="80" fill="#e4067e" /><circle cx="85" cy="85" r="66" fill="none" stroke="#fff" strokeWidth="3" strokeDasharray="7 8" /><path d="m54 88 22 22 42-48" fill="none" stroke="#fff" strokeWidth="13" strokeLinecap="round" strokeLinejoin="round" /></svg>
          <div style={{ textAlign: 'center' }}>
            <H>{t('Sent. Thank you!')}</H>
            {draft.recipient && <p className="sub">{t('Sent to')} {draft.recipient}</p>}
            <p className="sub">{t(way === 'guest' ? 'Guests do not collect points.' : 'You get points when the city confirms it.')}</p>
          </div>
          <button className="tick" onClick={() => navigator.clipboard?.writeText(id ?? '')}><span className="lab">{t('Reference number, tap to copy')}</span><b>{id}</b></button>
          {photoNote && <div className="note" role="alert">{photoNote}</div>}
          <div className="grow" />
          <div className="foot"><Go onClick={() => go(`status/${s?.ref ?? id}`)}>{t('Follow this report')}</Go><Txt onClick={() => go('home')}>{t('Report something else')}</Txt></div>
        </>
      );
      break;
    }
    case 'mine':
      body = <Mine {...ui} sent={sent} />;
      break;
    case 'find':
      body = <Find {...ui} />;
      break;
    case 'status':
      body = <Status key={id} {...ui} reference={id ?? ''} own={sent.find((s) => s.ref === id)} />;
      break;
    case 'map':
      body = <PublicMap {...ui} way={way} />;
      break;
    case 'account':
      body = <Account {...ui} way={way} person={person} standing={standing} sent={sent} LangButton={LangButton}
        onOut={() => { memory.setWay(null); setWay(null); memory.setPerson(null); setPerson(null); memory.setStanding(null); setStanding(null); started.current = false; go('welcome'); }} />;
      break;
    default:
      body = (
        <>
          <div className="homebar">
            {editing ? <button className="rb" aria-label={t('Back')} onClick={() => go('check')}><Icon name="back" /></button>
              : way === 'signed' ? <span className="hello"><Svg code={avatar} />{person?.name?.split(' ')[0] ?? person?.label}</span> : <LangButton />}
            {standing ? <button className="acc" onClick={() => go('account')}><Icon name="star" />{t('# points', standing.points)}</button>
              : way !== 'signed' && <button className="acc" onClick={() => go('login')}><Icon name="mask" />{t('Sign in')}</button>}
          </div>
          <H>{t('What did you notice?')}</H>
          <p className="sub">{t('Tap a picture to start.')}</p>
          <div className="home">
            {CATS.map((c) => (
              <button key={c} className="tile" onClick={() => {
                if (editing) { change({ cat: c, what: '', other: '', size: '' }); } else { setDraft(newDraft(c)); started.current = true; }
                go(editing ? 'what' : 'where');
              }}><Svg code={art[c]} /><b>{t(TILE[c])}</b></button>
            ))}
          </div>
          {!editing && <Tabs at={0} />}
        </>
      );
  }

  return <div className="hio" ref={root}><main key={screen} className={`scr${screen === 'done' ? ' done' : ''}`}>{body}</main></div>;
};

type Ui = {
  t: (key: string, ...n: number[]) => string;
  go: (s: string) => void;
  next: (s: string) => void;
  Back: (p: { to: string; step?: number }) => ReactNode;
  H: (p: { children: ReactNode }) => ReactNode;
  Go: (p: { children: ReactNode; onClick: () => void; disabled?: boolean; plum?: boolean }) => ReactNode;
  Txt: (p: { children: ReactNode; onClick: () => void }) => ReactNode;
  Tabs: (p: { at: number }) => ReactNode;
  Thumb: (p: { cat: Cat; size?: number }) => ReactNode;
};
type DraftUi = Ui & { draft: Draft; change: (part: Partial<Draft>) => void };

/** The helpers of Ui are functions that return markup. They are called, not mounted, so fields keep their focus. */
const Login = ({ t, go, Back, H, onChoose, back }: Ui & { onChoose: (p: Person) => void; back: string }) => {
  const [persons, setPersons] = useState<Person[]>([]);
  const [error, setError] = useState('');
  useEffect(() => { reportsApi.testPersons().then(setPersons).catch((e) => setError(message(e, t))); }, [t]);
  return (
    <>
      {Back({ to: back })}<span className="mocklab">{t('Smart-ID demo')}</span>
      {H({ children: t('Sign in with Smart-ID') })}
      <p className="sub">{t('Demo environment. Choose an invented person. Nobody types a PIN or a personal code here.')}</p>
      {error && <p className="err" role="alert">{error}</p>}
      {persons.map((p) => (
        <button key={p.document} className="way" style={{ minHeight: 72 }} onClick={() => onChoose(p)}><span className="ic"><Icon name="mask" /></span><b>{p.label}</b></button>
      ))}
      <div className="grow" /><div className="foot"><button className="txt" onClick={() => go('privacy')}>{t('Continue without signing in')}</button></div>
    </>
  );
};

const Where = ({ t, Back, H, Go, Txt, draft, change, onYes, back }: DraftUi & { onYes: (near: boolean) => void; back: string }) => {
  const [state, setState] = useState<'ask' | 'busy' | 'found' | 'denied' | 'type'>(draft.address ? 'found' : 'ask');
  const [hint, setHint] = useState('');
  const [error, setError] = useState('');
  const [text, setText] = useState(draft.address);
  const [near, setNear] = useState(false);
  const call = useRef(0);

  const look = (spot: Spot) => {
    const mine = ++call.current;
    change({ spot });
    setState('busy');
    reportsApi.place(spot.latitude, spot.longitude, codeOf(draft)).then((p) => {
      if (mine !== call.current) return;
      change({ spot, address: p.fullAddress, recipient: p.recipientName });
      setText(p.fullAddress);
      setHint(''); setState('found');
    }).catch((e) => {
      if (mine !== call.current) return;
      const outside = e instanceof ApiProblem && e.status === 404;
      if (outside) { change({ spot: undefined, address: '', recipient: undefined }); }
      setHint(t(outside ? 'This place is not in Estonia' : 'Something went wrong. Try again.'));
      setState('type');
    });
    const d = 0.002;
    reportsApi.map(spot.longitude - d, spot.latitude - d, spot.longitude + d, spot.latitude + d, codeOf(draft))
      .then((list) => mine === call.current && setNear(list.some((p) => p.status !== 'REJECTED' && p.status !== 'RESOLVED'))).catch(() => undefined);
  };
  const locate = () => {
    setState('busy');
    if (!navigator.geolocation) { setState('denied'); return; }
    navigator.geolocation.getCurrentPosition((p) => look({ latitude: p.coords.latitude, longitude: p.coords.longitude }),
      () => setState('denied'), { enableHighAccuracy: true, timeout: 10000 });
  };
  const typed = () => {
    if (!text.trim()) { setError(t('Fill in the address')); return; }
    /* A typed address without a pin. The server finds the municipality. */
    change({ address: text.trim() });
    setState('found');
  };

  return (
    <>
      {Back({ to: back, step: 1 })}
      {H({ children: t(state === 'found' ? 'Is this the place?' : state === 'type' ? 'Type the address' : 'Where is it?') })}
      {state === 'type' ? (
        <>
          {hint && <div className="note" role="alert">{hint}</div>}
          <p className="sub" style={{ marginBottom: 18 }}><label htmlFor="address">{t('Street and number, or a landmark')}</label></p>
          <Input id="address" className="inp" maxLength={255} autoComplete="off" value={text} onChange={(e) => { setText(e.target.value); setError(''); }} onPressEnter={typed} />
          <p className="err" role="alert">{error}</p>
        </>
      ) : (
        <>
          <div className="scene"><MapView center={draft.spot ?? TALLINN} zoom={draft.spot ? 17 : 11} pin={draft.spot} onPin={look} label={() => ''} /></div>
          {draft.spot && state === 'found' && <p className="lab" style={{ marginBottom: 10 }}>{t('Move the pin to the exact place.')}</p>}
          {state === 'busy' && <p role="status" className="sub">{t('Loading')}</p>}
          {state === 'found' && (
            <div className="found ok"><span className="ic"><Icon name="pin" /></span><span><b>{draft.address}</b>
              <small className="lab">{draft.recipient === 'Coordinator decides' || !draft.recipient ? t('The coordinator decides who it is for') : `${t('It will go to')}: ${draft.recipient}`}</small></span></div>
          )}
          {state === 'denied' && <div className="found w"><span className="ic"><Icon name="warn" /></span><span><b>{t('The phone did not share its location')}</b><small className="lab">{t('Type the address, or allow location in the phone settings.')}</small></span></div>}
        </>
      )}
      <div className="grow" />
      <div className="foot">
        {state === 'found' ? <>{Go({ children: t('Yes, this is it'), onClick: () => onYes(near) })}{Txt({ children: t('Choose another place'), onClick: () => setState('type') })}</>
          : state === 'type' ? <>{Go({ children: t('Continue'), onClick: typed })}{Txt({ children: t('I am here now'), onClick: locate })}</>
            : <>{Go({ children: t(state === 'denied' ? 'Try location again' : 'I am here now'), onClick: locate, disabled: state === 'busy' })}{Txt({ children: t('Type the address'), onClick: () => setState('type') })}</>}
      </div>
    </>
  );
};

const statusName = (p: Pick<MapPoint, 'status'>) => STEP[p.status];
const day = (iso: string) => new Date(iso).toLocaleDateString(document.documentElement.lang || 'en', { day: 'numeric', month: 'short' });
const meToo = (id: number, way: Way | null) => (way === 'guest' || !way ? Promise.resolve(null) : reportsApi.sight(id, memory.device()));

const PointCard = ({ t, Thumb, point, way, go, text }: Ui & { point: MapPoint; way: Way | null; text: string }) => {
  const [detail, setDetail] = useState<MapDetail | null>(null);
  const [urls, setUrls] = useState<string[]>([]);
  const [error, setError] = useState('');
  useEffect(() => {
    let live = true;
    const made: string[] = [];
    setDetail(null); setUrls([]);
    reportsApi.mapDetail(point.id).then(async (d) => {
      if (!live) return;
      setDetail(d);
      for (const p of d.photos) {
        const u = await reportsApi.mapPhotoUrl(point.id, p.id);
        made.push(u);
        if (live) setUrls([...made]); else URL.revokeObjectURL(u);
      }
    }).catch(() => undefined);
    return () => { live = false; made.forEach((u) => URL.revokeObjectURL(u)); };
  }, [point.id]);
  const cat = catOf(point.categoryCode);
  return (
    <div className="card2">
      <div className="recap">{Thumb({ cat })}<div><b>{t(NAME[cat])}</b><br /><span>{detail?.address ?? point.municipalityName}, {day(point.submittedOn)}</span>
        <span className="lab">{SIGN[point.status]} {t(statusName(point))}</span><span className="lab">{t('Seen by #', point.seenBy)}</span></div></div>
      {detail?.description ? <p style={{ marginTop: 10 }}>{detail.description}</p> : <p className="lab" style={{ marginTop: 10 }}>{t('The text is shown after the city has checked it.')}</p>}
      {urls.length > 0 && <ul className="ph">{urls.map((u) => <li key={u}><img src={u} alt={t('Photo')} /></li>)}</ul>}
      {error && <p className="err" role="alert">{error}</p>}
      {way && way !== 'guest' && !['RESOLVED', 'REJECTED'].includes(point.status) && (
        <button className="go pl" onClick={() => meToo(point.id, way).then(() => go('seen')).catch((e) => setError(message(e, t)))}>{text}</button>
      )}
    </div>
  );
};

const Near = (p: Ui & { draft: Draft; way: Way | null }) => {
  const { t, Back, H, Go, next, draft } = p;
  const [list, setList] = useState<MapPoint[] | null>(null);
  useEffect(() => {
    const s = draft.spot ?? TALLINN, d = 0.002;
    reportsApi.map(s.longitude - d, s.latitude - d, s.longitude + d, s.latitude + d, codeOf(draft))
      .then((l) => setList(l.filter((x) => x.status !== 'REJECTED' && x.status !== 'RESOLVED').slice(0, 3))).catch(() => setList([]));
  }, [draft]);
  return (
    <>
      {Back({ to: 'where', step: 1 })}{H({ children: t('Is it one of these?') })}
      <p className="sub" style={{ marginBottom: 14 }}>{t('Reports of other people, without names.')}</p>
      {list === null ? <p role="status">{t('Loading')}</p> : list.map((x) => <PointCard key={x.id} {...p} point={x} text={t('Yes, I see it too')} />)}
      <div className="grow" /><div className="foot">{Go({ children: t('No, mine is different'), onClick: () => next('what') })}</div>
    </>
  );
};

const Words = ({ t, go, Back, H, Go, draft, change }: DraftUi) => {
  const [error, setError] = useState('');
  return (
    <>
      {Back({ to: 'what', step: 2 })}{H({ children: t('In your own words') })}
      <Input.TextArea aria-label={t('In your own words')} className="inp" style={{ marginTop: 18 }} maxLength={500} showCount autoSize={{ minRows: 5 }} value={draft.words} onChange={(e) => { change({ words: e.target.value }); setError(''); }} />
      <p className="err" role="alert">{error}</p>
      <div className="grow" /><div className="foot">{Go({ children: t('Continue'), onClick: () => { const n = draft.words.trim().length; if (n > 0 && n < 10) setError(t('Write at least 10 characters')); else go('what'); } })}</div>
    </>
  );
};

const MB8 = 8 * 1024 * 1024;

const Photos = ({ t, next, Back, H, Go, Txt, draft, change }: DraftUi) => {
  const [note, setNote] = useState('');
  const [skip, setSkip] = useState(false);
  const [urls, setUrls] = useState<string[]>([]);
  const photos = useRef(draft.photos);
  photos.current = draft.photos;
  useEffect(() => {
    const made = draft.photos.map((f) => URL.createObjectURL(f));
    setUrls(made);
    return () => made.forEach((u) => URL.revokeObjectURL(u));
  }, [draft.photos]);
  const add = (f: File) => {
    if (f.size > MB8 || !['image/jpeg', 'image/png'].includes(f.type)) setNote(`${t('One photo is too large.')} ${t('Choose another one. The other photos are kept.')}`);
    else if (photos.current.length < 5) { photos.current = [...photos.current, f]; change({ photos: photos.current }); setNote(''); }
    return false;
  };
  const pick = (button: ReactNode, camera: boolean) => (
    <Upload accept="image/jpeg,image/png" multiple={!camera} capture={camera ? 'environment' : undefined} showUploadList={false} beforeUpload={add}>{button}</Upload>
  );
  const n = draft.photos.length;
  return (
    <>
      {Back({ to: 'what', step: 3 })}{H({ children: n ? t(n === 1 ? '# photo added' : '# photos added', n) : t('Take a photo') })}
      <p className="sub">{t('Up to 5 photos. Keep faces and car plates out of the picture.')}</p>
      {n < 5 && pick(<button className={n ? 'cam sm' : 'cam'}><Icon name="cam" />{t(n ? 'Add another' : 'Open camera')}</button>, true)}
      {note && <div className="note" role="alert">{note}</div>}
      {skip && !n && <div className="note" role="alert"><b>{t('Without a photo the trust level is lower.')}</b> {t('You can still send the report.')}</div>}
      {n > 0 && <ul className="ph">{urls.map((u, k) => <li key={u}><img src={u} alt={`${t('Photo')} ${k + 1}`} /><button aria-label={t('Remove photo #', k + 1)} onClick={() => change({ photos: draft.photos.filter((_, j) => j !== k) })}><Icon name="x" /></button></li>)}</ul>}
      <div className="grow" />
      <div className="foot">
        {n ? Go({ children: t('Continue'), onClick: () => next('contact') }) : pick(<button className="go pl">{t('Choose from the phone')}</button>, false)}
        {n ? n < 5 && pick(<button className="txt">{t('Choose from the phone')}</button>, false)
          : Txt({ children: t(skip ? 'Continue without a photo' : 'Skip, I have no photo'), onClick: () => (skip ? next('contact') : setSkip(true)) })}
      </div>
    </>
  );
};

const Contact = ({ t, Back, H, Go, draft, change, anonymous, onNext }: DraftUi & { anonymous: boolean; onNext: () => void }) => {
  const [error, setError] = useState('');
  const w = draft.contactWay;
  const option = (k: 'mail' | 'call' | 'no', icon: string, text: string, sub: string) => (
    <button className="way" aria-pressed={w === k} onClick={() => { change({ contactWay: k, contact: '' }); setError(''); }}><span className="ic"><Icon name={icon} /></span><span><b>{t(text)}</b><span className="lab">{t(sub)}</span></span></button>
  );
  const ok = () => {
    const c = draft.contact.trim();
    if (w === 'mail' && !/^\S+@\S+\.\S+$/.test(c)) setError(t('Write an email like name@example.com'));
    else if (w === 'call' && c.replace(/\D/g, '').length < 7) setError(t('Write a phone number with at least 7 digits'));
    else onNext();
  };
  return (
    <>
      {Back({ to: 'photo', step: 4 })}{H({ children: t('May we contact you?') })}<p className="sub">{t('Only about this report.')}</p>
      {option('mail', 'mail', 'By email', 'We write if we have a question')}
      {option('call', 'call', 'By phone', 'We call if we have a question')}
      {option('no', 'no', 'No, thanks', 'You can follow the report here')}
      {(w === 'mail' || w === 'call') && (
        <>
          <p style={{ margin: '16px 0 8px' }}><label htmlFor="contact"><b>{t(w === 'mail' ? 'Your email' : 'Your phone number')}</b></label></p>
          <Input id="contact" className="inp" maxLength={255} type={w === 'mail' ? 'email' : 'tel'} autoComplete={w === 'mail' ? 'email' : 'tel'} value={draft.contact} onChange={(e) => change({ contact: e.target.value })} />
          <p className="err" role="alert">{error}</p>
          {anonymous && <div className="note">{t('The coordinator will see this contact, even in an anonymous report.')}</div>}
        </>
      )}
      <div className="grow" /><div className="foot">{Go({ children: t('Continue'), onClick: ok, disabled: !w })}</div>
    </>
  );
};

const Check = ({ t, go, Back, H, Go, Txt, draft, change, signed, way, send, signing, children }: DraftUi & {
  signed: boolean; way: Way | null; send: () => Promise<void>; signing: boolean; children: ReactNode;
}) => {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const row = (label: string, value: string, to: string) => (
    <button onClick={() => go(to)}><span><span className="lab">{t(label)}</span><b>{value}</b></span><span style={{ flex: 'none', color: 'var(--plum)', fontSize: 15, fontWeight: 700 }}>{t('Change')}</span></button>
  );
  const facts: [boolean, string][] = [[signed, 'Signed'], [draft.photos.length > 0, 'Photo'], [!!draft.spot, 'Place']];
  const level = facts.filter(([on]) => on).length;
  const trust = ['Very low trust', 'Low trust', 'Good trust', 'High trust'][level];
  const details = [draft.what === ELSE ? draft.other : t(draft.what), t(draft.size), draft.words].filter(Boolean).join(', ') || detailsOf(draft);
  const submit = () => {
    setBusy(true); setError('');
    send().catch((e) => {
      setError(e instanceof ApiProblem && e.status === 429
        ? `${t('You have sent enough for now.')} ${t('Try again in # minutes, or sign in. Nothing is lost.', Math.ceil((e.retryAfter ?? 60) / 60))}`
        : e instanceof ApiProblem && e.status < 500 ? e.message
          : `${t('The report was not sent.')} ${t('Check the connection and send it again. It will not be sent twice.')}`);
    }).finally(() => setBusy(false));
  };
  return (
    <>
      {Back({ to: 'contact', step: 5 })}{H({ children: t('Is everything right?') })}
      <div className="rows">
        {row('Problem', t(NAME[draft.cat]), 'home')}{row('Place', draft.address, 'where')}{row('Details', details, 'what')}
        {row('Photos', draft.photos.length ? t('# attached', draft.photos.length) : t('None'), 'photo')}
        {row('Contact', draft.contactWay === 'no' || !draft.contact ? t('No, thanks') : draft.contact, 'contact')}
      </div>
      <div className="tr"><b>{t(trust)}</b><div className="bars">{facts.map(([on, f]) => <span key={f} className={on ? 'on' : ''}><Icon name={on ? 'ok' : 'x'} /><span className="vh">{t(on ? 'Yes,' : 'No,')}</span>{t(f)}</span>)}</div></div>
      {way === 'signed' && <label className="sw"><span><b>{t('Send anonymously')}</b><br /><span className="lab">{t('Only this report')}</span></span><Switch checked={draft.anonymous} onChange={(anonymous) => change({ anonymous })} aria-label={t('Send anonymously')} /></label>}
      {error ? <div className="note" role="alert">{error}</div>
        : !signed && <div className="note" role="status"><b>{t('Anonymous reports have a lower trust level.')}</b> {t('The municipality may check them longer.')}</div>}
      <div className="grow" />
      <div className="foot">
        {Go({ children: t(busy ? 'Sending' : signed ? 'Sign and send' : 'Send anonymously'), onClick: submit, disabled: busy || signing })}
        {way !== 'signed' && Txt({ children: t('Sign in instead'), onClick: () => go('login') })}
      </div>
      {children}
    </>
  );
};

const SignSheet = ({ t, report, person, onSigned, onAnonymous, onClose }: Ui & {
  report: NewReport; person: Person; onAnonymous: () => void; onClose: () => void;
  onSigned: (ref: string, key: string, name?: string, standing?: Standing) => void;
}) => {
  const [code, setCode] = useState('');
  const [end, setEnd] = useState('');
  const [attempt, setAttempt] = useState(0);
  const latest = useRef({ report, onSigned });
  latest.current = { report, onSigned };
  const head = useRef<HTMLHeadingElement>(null);
  useEffect(() => { head.current?.focus(); }, [end]);
  useEffect(() => {
    let live = true;
    let timer: ReturnType<typeof setTimeout>;
    setCode(''); setEnd('');
    const fail = (e: unknown) => live && setEnd(e instanceof ApiProblem ? e.message : t('Something went wrong. Try again.'));
    /* Strict mode mounts twice in development. The delay lets the first mount go without a request. */
    timer = setTimeout(() => {
      reportsApi.startSigning(latest.current.report, person.document).then((s) => {
        if (!live) return;
        setCode(s.verificationCode);
        const ask = () => reportsApi.signing(s.token).then((r) => {
          if (!live) return;
          if (r.state === 'PENDING') timer = setTimeout(ask, 1500);
          else if (r.state === 'SIGNED' && r.referenceNumber && r.submissionKey) latest.current.onSigned(r.referenceNumber, r.submissionKey, r.name, r.standing);
          else setEnd(t(r.state === 'REFUSED' ? 'You refused in Smart-ID. Nothing was sent.' : r.state === 'EXPIRED' ? 'The time is over. Nothing was sent.' : 'The signature could not be verified. Nothing was sent.'));
        }).catch(fail);
        timer = setTimeout(ask, 1500);
      }).catch(fail);
    }, 50);
    return () => { live = false; clearTimeout(timer); };
  }, [attempt, person.document, t]);
  return (
    <>
      <div className="dim" />
      <section className="sheet" role="dialog" aria-modal="true" aria-label={t('Confirm in Smart-ID')}>
        <span className="mocklab">{t('Smart-ID demo')}</span>
        <h1 tabIndex={-1} ref={head}>{t(end ? 'Not signed' : code ? 'Confirm in Smart-ID' : 'Waiting for Smart-ID')}</h1>
        {end ? <><p role="alert">{end}</p><button className="go pl" onClick={() => setAttempt((n) => n + 1)}>{t('Try Smart-ID again')}</button></>
          : <><p>{t('Check that your phone shows the same code.')}</p><p className="code" role="status">{code || '····'}</p></>}
        <button className="txt" onClick={onAnonymous}>{t('Send anonymously instead')}</button>
        {end && <button className="txt" onClick={onClose}>{t('Back to my report')}</button>}
      </section>
    </>
  );
};

const Mine = ({ t, go, H, Go, Tabs, Thumb, sent }: Ui & { sent: SentReport[] }) => {
  const [states, setStates] = useState<Record<string, Report['status']>>({});
  useEffect(() => {
    sent.slice(0, 20).forEach((s) => reportsApi.one(s.ref).then((r) => setStates((x) => ({ ...x, [s.ref]: r.status }))).catch(() => undefined));
  }, [sent]);
  return (
    <>
      {H({ children: t('My reports') })}
      <p className="sub" style={{ marginBottom: 16 }}>{t(sent.length ? 'Sent from this phone, newest first' : 'Reports you send from this phone appear here.')}</p>
      {sent.map((m) => (
        <button key={m.ref} className="mine" style={{ marginBottom: 12 }} onClick={() => go(`status/${m.ref}`)}>
          <span className="recap" style={{ margin: 0, flex: 'none' }}>{Thumb({ cat: m.cat, size: 60 })}</span>
          <span><b>{m.address}</b><small>{m.ref}</small><small>{states[m.ref] ? t(STEP[states[m.ref]]) : ''}</small></span>
        </button>
      ))}
      <div className="grow" /><div style={{ margin: '14px 0 18px' }}>{Go({ children: t('Find another report'), onClick: () => go('find'), plum: true })}</div>
      {Tabs({ at: 2 })}
    </>
  );
};

const Find = ({ t, go, Back, H, Go }: Ui) => {
  const [ref, setRef] = useState('');
  const [error, setError] = useState('');
  const find = () => {
    if (!ref.trim()) return;
    reportsApi.one(ref.trim()).then((r) => go(`status/${r.referenceNumber}`))
      .catch((e) => setError(e instanceof ApiProblem && e.status === 404 ? t('No report with this reference number') : t('Something went wrong. Try again.')));
  };
  return (
    <>
      {Back({ to: 'mine' })}{H({ children: t('Find by reference number') })}
      <p className="sub" style={{ marginBottom: 18 }}><label htmlFor="ref">{t('For example R-2026-K7QX2M')}</label></p>
      <Input id="ref" className="inp" autoComplete="off" autoCapitalize="characters" value={ref} onChange={(e) => { setRef(e.target.value); setError(''); }} onPressEnter={find} />
      <p className="err" role="alert">{error}</p>
      <div className="grow" /><div className="foot">{Go({ children: t('Find'), onClick: find, plum: true })}</div>
    </>
  );
};

const Status = ({ t, go, Back, H, Go, Txt, reference, own }: Ui & { reference: string; own?: SentReport }) => {
  const [report, setReport] = useState<Report | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [moves, setMoves] = useState<Forwarding[]>([]);
  const [photos, setPhotos] = useState<(PhotoInfo & { url: string })[]>([]);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [again, setAgain] = useState<string | null>(null);
  const key = own?.key;

  const load = useCallback(() => {
    Promise.all([reportsApi.one(reference), reportsApi.messages(reference), reportsApi.forwardings(reference)])
      .then(([r, m, f]) => { setReport(r); setMessages(m); setMoves(f); })
      .catch((e) => setError(e instanceof ApiProblem && e.status === 404 ? t('No report with this reference number') : t('Something went wrong. Try again.')));
  }, [reference, t]);
  useEffect(load, [load]);
  useEffect(() => {
    if (!key) return undefined;
    let live = true;
    const made: string[] = [];
    reportsApi.photos(reference, key).then(async (list) => {
      const got: (PhotoInfo & { url: string })[] = [];
      for (const p of list) {
        const url = await reportsApi.photoUrl(reference, p.id, key);
        made.push(url);
        got.push({ ...p, url });
      }
      if (live) setPhotos(got); else made.forEach((u) => URL.revokeObjectURL(u));
    }).catch(() => undefined);
    return () => { live = false; made.forEach((u) => URL.revokeObjectURL(u)); };
  }, [reference, key]);

  if (error) return <>{Back({ to: 'mine' })}{H({ children: error })}</>;
  if (!report) return <>{Back({ to: 'mine' })}<p role="status">{t('Loading')}</p></>;

  const updates = messages.filter((m) => m.kind === 'UPDATE');
  const last = updates[updates.length - 1];
  const place = { RECEIVED: 0, ACCEPTED: 1, IN_PROGRESS: 2, RESOLVED: 4, REJECTED: 0 }[report.status];
  const now = report.status !== 'RESOLVED' && last && place >= 2 ? 3 : place;
  const step = (k: number, text: string, sub: ReactNode, icon: string) => (
    <li key={text} className={k < now ? 'did' : k === now ? 'now' : ''} aria-current={k === now ? 'step' : undefined}><span className="st"><Icon name={icon} /></span><b>{t(text)}</b><span className="lab">{sub}</span></li>
  );
  const title = report.status === 'REJECTED' ? 'This one will not be fixed'
    : now === 3 ? 'There is news' : ['You reported it', 'We accepted it', 'People are working on it', 'There is news', 'It is done'][place];
  const days = report.daysToRespond;
  const before = photos.find((p) => p.kind === 'BEFORE');
  const after = [...photos].reverse().find((p) => p.kind === 'AFTER');
  return (
    <>
      {Back({ to: 'mine' })}{H({ children: t(title) })}
      <p className="sub">{report.address}<br />{report.referenceNumber}<br />{t('Sent to')} {report.recipientName}</p>
      {moves.map((f) => <div key={f.sysCreatedAt} className="note">{t('Forwarded to')} <b>{f.toName}</b><br />{f.reason}</div>)}
      {days !== undefined && <div className="note" role="status"><b>{days >= 0 ? t('# days left to answer', days) : t('The answer is late by # days', -days)}</b></div>}
      {report.status === 'REJECTED' ? <div className="note"><b>{t('Rejected')}</b><br />{report.rejectionReason}</div> : (
        <ol className="jr" style={{ padding: 0, marginTop: 22 }}>
          {step(0, 'Reported', day(report.submittedAt), 'send')}
          {step(1, 'Accepted', now > 0 ? report.recipientName : t('Not yet'), 'inbox')}
          {step(2, 'In progress', now >= 2 ? '' : t('Not yet'), 'cone')}
          {step(3, 'Update', last ? <>{last.text}{last.expectedOn ? <><br />{t('Expected')}: {day(last.expectedOn)}</> : null}</> : t('Expected date appears here'), 'news')}
          {step(4, 'Done', now > 3 ? '' : t('Not yet'), 'ok')}
        </ol>
      )}
      {report.status === 'RESOLVED' && (before || after) && (
        <div className="ba">{before && <div><img src={before.url} alt={t('Before')} /><b>{t('Before')}</b></div>}{after && <div><img src={after.url} alt={t('After')} /><b>{t('After')}</b></div>}</div>
      )}
      {note && <div className="note" role="status">{note}</div>}
      {again !== null && (
        <>
          <p style={{ margin: '14px 0 8px' }}><label htmlFor="again"><b>{t('What is still wrong?')}</b></label></p>
          <Input.TextArea id="again" className="inp" maxLength={500} autoSize={{ minRows: 3 }} value={again} onChange={(e) => setAgain(e.target.value)} />
        </>
      )}
      <div className="grow" />
      {key && (
        <div className="foot">
          {report.status === 'RESOLVED' ? (again === null
            ? <><p style={{ textAlign: 'center' }}><b>{t('Is it really fixed?')}</b></p>{Go({ children: t('Yes, all good'), onClick: () => go('mine') })}{Txt({ children: t('No, still a problem'), onClick: () => setAgain('') })}</>
            : Go({ children: t('No, still a problem'), disabled: !again.trim(), onClick: () => { reportsApi.remains(reference, key, again).then(() => { setAgain(null); setNote(t('We will look again')); load(); }).catch((e) => setNote(message(e, t))); } }))
            : report.status !== 'REJECTED' && Go({ plum: true, children: t('Remind them'), onClick: () => { reportsApi.remind(reference, key).then(() => setNote(t('Reminder sent'))).catch((e) => setNote(e instanceof ApiProblem && e.status === 409 ? t('A reminder is possible once in 3 days.') : message(e, t))); } })}
        </div>
      )}
    </>
  );
};

const PublicMap = (p: Ui & { way: Way | null }) => {
  const { t, H, Tabs, way } = p;
  const [points, setPoints] = useState<MapPoint[]>([]);
  const [filter, setFilter] = useState<Cat | ''>('');
  const [list, setList] = useState(false);
  const [picked, setPicked] = useState<number | null>(null);
  const [error, setError] = useState('');
  const call = useRef(0);
  const load = useCallback((b: Box) => {
    const mine = ++call.current;
    reportsApi.map(b.west, b.south, b.east, b.north).then((x) => { if (mine === call.current) { setPoints(x); setError(''); } }).catch((e) => setError(message(e, t)));
  }, [t]);
  const [center, setCenter] = useState(TALLINN);
  useEffect(() => {
    navigator.permissions?.query({ name: 'geolocation' }).then((s) => {
      if (s.state === 'granted') navigator.geolocation.getCurrentPosition((x) => setCenter({ latitude: x.coords.latitude, longitude: x.coords.longitude }));
    }).catch(() => undefined);
  }, []);
  const shown = points.filter((x) => !filter || catOf(x.categoryCode) === filter);
  const one = shown.find((x) => x.id === picked);
  const label = (x: MapPoint) => `${t(NAME[catOf(x.categoryCode)])}, ${t(statusName(x))}`;
  return (
    <>
      <div className="homebar" style={{ margin: 0 }}>{H({ children: t('Map') })}<button className="lng" onClick={() => setList(!list)}><Icon name={list ? 'map' : 'list'} />{t(list ? 'Map' : 'List')}</button></div>
      <div className="fl" role="group" aria-label={t('Filter')}>
        <button aria-pressed={!filter} onClick={() => setFilter('')}>{t('All')}</button>
        {CATS.map((c) => <button key={c} aria-pressed={filter === c} onClick={() => setFilter(c)}>{t(TILE[c])}</button>)}
      </div>
      {error && <p className="err" role="alert">{error}</p>}
      <div className="bigmap" style={list ? { display: 'none' } : undefined}><MapView center={center} zoom={11} points={shown} onPoint={setPicked} onBox={load} label={label} /></div>
      {list ? <div style={{ marginTop: 12 }}>{shown.length ? shown.slice(0, 20).map((x) => <PointCard key={x.id} {...p} way={way} point={x} text={t('I see it too')} />) : <p className="sub">{t('Nothing here yet.')}</p>}</div> : (
        <>
          <p className="legend">{(Object.keys(SIGN) as Report['status'][]).map((s) => <span key={s}><i className={`mk ${s}`} style={{ borderWidth: 0 }} />{SIGN[s]} {t(STEP[s])}</span>)}</p>
          {one ? <PointCard {...p} way={way} point={one} text={t('I see it too')} /> : <p className="sub" style={{ marginBottom: 12 }}>{t(shown.length ? 'Tap a pin to see the report.' : 'Nothing here yet.')}</p>}
        </>
      )}
      {Tabs({ at: 1 })}
    </>
  );
};

const Account = ({ t, go, H, Go, Txt, Tabs, way, person, standing, sent, LangButton, onOut }: Ui & {
  way: Way | null; person: Person | null; standing: Standing | null; sent: SentReport[]; LangButton: () => ReactNode; onOut: () => void;
}) => {
  if (!way || way === 'guest') {
    return (
      <>
        <div className="homebar">{H({ children: t('Profile') })}{LangButton()}</div>
        <div className="welc" style={{ maxHeight: 260, marginTop: 0 }}><Svg code={art.hero} /></div>
        <h2 style={{ margin: 0 }}>{t('Grow your level')}</h2><p className="sub">{t('Guests do not collect points.')}</p>
        <div className="grow" />{Go({ children: t('Sign in'), onClick: () => go('login') })}{Txt({ children: t('Switch account'), onClick: () => go('privacy') })}{Tabs({ at: 3 })}
      </>
    );
  }
  const p = standing ?? { points: 0, level: 0, nextLevelAt: 50 };
  const from = [0, 50, 150, 300][p.level];
  const x = 24 + (p.nextLevelAt ? (p.points - from) / (p.nextLevelAt - from) : 1) * 254;
  return (
    <>
      <div className="prof">
        <svg className="bgp" viewBox="0 0 390 330" preserveAspectRatio="xMidYMax slice" aria-hidden="true"><rect width="390" height="330" fill="#342b60" /><path d="M195 120 60-20h50zM195 120 150-40h50zM195 120 250-40h50zM195 120 420 90v60zM195 120-30 90v60zM195 120-30 220v60zM195 120 420 220v60z" fill="#463c7c" /><path d="M0 286c80-30 250-30 390-6v60H0z" fill="#3f9774" /></svg>
        <div className="homebar" style={{ margin: 0 }}><span className="vh">{t('Profile')}</span><span />{LangButton()}</div>
        <Svg code={avatar} />{H({ children: way === 'signed' ? person?.name ?? person?.label : t('Device number on') })}
        <div><span className="rank"><Icon name="star" />{t('Level #', p.level)}, {t('# points', p.points)}</span></div>
      </div>
      <div className="lv" style={{ marginTop: 12 }}>
        <b>{t('# points', p.points)}</b><span className="lab">{p.nextLevelAt ? t('# points to the next level', p.nextLevelAt - p.points) : t('Top level')}</span>
        <svg viewBox="0 0 310 96" aria-hidden="true"><path d="M0 74c60-26 150 10 310-14v36H0z" fill="#3f9774" /><ellipse cx="286" cy="66" rx="17" ry="6" fill="#150f33" /><path d="M286 66V14" stroke="#fff" strokeWidth="4" strokeLinecap="round" /><path d="m288 14 22 9-22 9z" fill="#e4067e" /><path d={`M24 64H${x}`} stroke="#fff" strokeWidth="3" strokeDasharray="2 9" strokeLinecap="round" /><circle cx={x} cy="62" r="10" fill="#fff" /></svg>
      </div>
      <h2>{t('Medals')}</h2>
      <ul className="meds">
        <li className={sent.length ? '' : 'off'}><Svg code={sent.length ? medals.first : medals.off} />{t('First report')}</li>
        <li className={p.points >= 150 ? '' : 'off'}><Svg code={p.points >= 150 ? medals.trusted : medals.off} />{t('Trusted, # points', 150)}</li>
        <li className={new Set(sent.map((s) => s.cat)).size === 6 ? '' : 'off'}><Svg code={new Set(sent.map((s) => s.cat)).size === 6 ? medals.fixed : medals.off} />{t('All six types')}</li>
      </ul>
      <h2>{t('How to get points')}</h2><p className="sub">{t('Confirmed report 10, with a photo 5 more, fixed 20 more. A false report resets the points.')}</p>
      {Txt({ children: t(way === 'signed' ? 'Sign out' : 'Switch account'), onClick: onOut })}{Tabs({ at: 3 })}
    </>
  );
};
