import { useCallback, useEffect, useRef, useState } from 'react';
import { Select, Space } from 'antd';
import { AppCard, AppTag, FieldItem, useAppNotification } from '@helex/ui';
import { reportsApi, type Category, type MapDetail, type MapPoint } from '../../reportsApi';
import { MapView, SIGN, type Box } from '../reports/MapView';
import '../reports/hio.css';
import { STATUS, StatusTag } from '../reports-coordinator/shared';

const TALLINN = { latitude: 59.437, longitude: 24.7536 };

/** The public map on a computer. The list beside it is for people who cannot use a map. */
export const WebMap = () => {
  const notify = useAppNotification();
  const [points, setPoints] = useState<MapPoint[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [category, setCategory] = useState<string>();
  const [picked, setPicked] = useState<number | null>(null);
  const [detail, setDetail] = useState<MapDetail | null>(null);
  const [photos, setPhotos] = useState<string[]>([]);
  const [box, setBox] = useState<Box | null>(null);
  const call = useRef(0);

  useEffect(() => {
    reportsApi.categories().then((c) => setCategories(c.filter((x) => x.onMap))).catch(() => undefined);
  }, []);
  const load = useCallback(() => {
    if (!box) return;
    const mine = ++call.current;
    reportsApi.map(box.west, box.south, box.east, box.north, category)
      .then((list) => { if (mine === call.current) setPoints(list); })
      .catch((e) => notify.error('Could not load the map', e.message));
  }, [box, category, notify]);
  useEffect(load, [load]);

  useEffect(() => {
    setDetail(null);
    setPhotos([]);
    if (picked === null) return undefined;
    let live = true;
    const made: string[] = [];
    reportsApi.mapDetail(picked).then(async (d) => {
      if (live) setDetail(d);
      for (const p of d.photos) {
        const url = await reportsApi.mapPhotoUrl(picked, p.id);
        made.push(url);
        if (live) setPhotos([...made]); else URL.revokeObjectURL(url);
      }
    }).catch(() => undefined);
    return () => { live = false; made.forEach((u) => URL.revokeObjectURL(u)); };
  }, [picked]);

  const name = (code: string) => categories.find((c) => c.code === code)?.name ?? code;
  return (
    <AppCard title="Reports on the map" style={{ margin: 24 }}
      extra={<Select aria-label="Category" placeholder="All categories" allowClear style={{ width: 220 }} value={category}
        onChange={(c) => { setCategory(c); setPicked(null); }} options={categories.map((c) => ({ value: c.code, label: c.name }))} />}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16 }}>
        {/* The class gives the pins their colours, its phone width is taken off here. */}
        <div className="hio" style={{ flex: '2 1 480px', maxWidth: 'none', minHeight: 560, height: 560, position: 'relative', margin: 0 }}>
          <MapView center={TALLINN} zoom={11} points={points} onPoint={setPicked} onBox={setBox} label={(p) => `${name(p.categoryCode)}, ${STATUS[p.status].text}`} />
        </div>
        <div style={{ flex: '1 1 280px', maxHeight: 560, overflow: 'auto' }}>
          <Space wrap style={{ marginBottom: 12 }}>
            {(Object.keys(STATUS) as MapPoint['status'][]).map((s) => <AppTag key={s} color={STATUS[s].color}>{SIGN[s]} {STATUS[s].text}</AppTag>)}
          </Space>
          {detail && (
            <AppCard size="small" title={name(detail.categoryCode)} style={{ marginBottom: 12 }}>
              <StatusTag status={detail.status} />
              <FieldItem label="Place">{detail.address ?? detail.municipalityName ?? '—'}</FieldItem>
              <FieldItem label="Recipient">{detail.recipientName}</FieldItem>
              <FieldItem label="Seen by">{detail.seenBy}</FieldItem>
              <p>{detail.description ?? 'The text is shown after the city has checked it.'}</p>
              {photos.map((u) => <img key={u} src={u} alt="Report" style={{ width: '100%', marginTop: 8 }} />)}
            </AppCard>
          )}
          <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {points.map((p) => (
              <li key={p.id}>
                <button type="button" aria-pressed={p.id === picked} onClick={() => setPicked(p.id)}
                  style={{ width: '100%', textAlign: 'left', padding: 12, border: 0, borderTop: '1px solid #e6e5ee', background: p.id === picked ? '#eeecf7' : 'none', font: 'inherit', cursor: 'pointer' }}>
                  <b>{name(p.categoryCode)}</b><br />{p.municipalityName ?? 'Place unknown'}, {p.submittedOn} <StatusTag status={p.status} />
                </button>
              </li>
            ))}
          </ul>
          {!points.length && <p>Nothing here. Move the map or choose another category.</p>}
        </div>
      </div>
    </AppCard>
  );
};
