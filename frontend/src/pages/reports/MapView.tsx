import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { MapPoint } from '../../reportsApi';

/** The public map of the Estonian Land and Spatial Development Board. */
const TILES = 'https://tiles.maaamet.ee/tm/wmts/1.0.0/kaart/default/GMC/{z}/{y}/{x}.png';
/** A sign beside the colour, so colour is not the only cue. */
export const SIGN = { RECEIVED: '!', ACCEPTED: '✓', IN_PROGRESS: '…', RESOLVED: '★', REJECTED: '×' } as const;

export interface Spot { latitude: number; longitude: number }
export interface Box { west: number; south: number; east: number; north: number }

interface Props {
  center: Spot;
  zoom?: number;
  /** A movable pin of the report that is being written. */
  pin?: Spot;
  onPin?: (spot: Spot) => void;
  points?: MapPoint[];
  onPoint?: (id: number) => void;
  onBox?: (box: Box) => void;
  label: (p: MapPoint) => string;
}

export const MapView = ({ center, zoom = 16, pin, onPin, points, onPoint, onBox, label }: Props) => {
  const div = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const marks = useRef<L.LayerGroup | null>(null);
  const calls = useRef({ onPin, onPoint, onBox, label });
  calls.current = { onPin, onPoint, onBox, label };

  useEffect(() => {
    const m = L.map(div.current!, { zoomControl: true }).setView([center.latitude, center.longitude], zoom);
    L.tileLayer(TILES, { maxZoom: 18, attribution: '&copy; Maa- ja Ruumiamet' }).addTo(m);
    marks.current = L.layerGroup().addTo(m);
    const box = () => {
      const b = m.getBounds();
      calls.current.onBox?.({ west: b.getWest(), south: b.getSouth(), east: b.getEast(), north: b.getNorth() });
    };
    m.on('moveend', box);
    m.on('click', (e) => calls.current.onPin?.({ latitude: e.latlng.lat, longitude: e.latlng.lng }));
    map.current = m;
    const size = new ResizeObserver(() => m.invalidateSize());
    size.observe(div.current!);
    box();
    return () => { size.disconnect(); m.remove(); map.current = null; };
    // The map is created once. Later changes of the centre are applied below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    map.current?.setView([center.latitude, center.longitude], Math.max(map.current.getZoom(), zoom));
  }, [center.latitude, center.longitude, zoom]);

  useEffect(() => {
    const group = marks.current;
    if (!group) return;
    group.clearLayers();
    points?.forEach((p) => {
      const icon = L.divIcon({ className: '', iconSize: [40, 40], html: `<span class="mk ${p.status}">${SIGN[p.status]}</span>` });
      L.marker([p.latitude, p.longitude], { icon, title: calls.current.label(p), alt: calls.current.label(p) })
        .on('click', () => calls.current.onPoint?.(p.id)).addTo(group);
    });
    if (pin) {
      const icon = L.divIcon({ className: '', iconSize: [40, 40], html: '<span class="mk me">●</span>' });
      const mark = L.marker([pin.latitude, pin.longitude], { icon, draggable: !!onPin }).addTo(group);
      mark.on('dragend', () => {
        const at = mark.getLatLng();
        calls.current.onPin?.({ latitude: at.lat, longitude: at.lng });
      });
    }
  }, [points, pin, onPin]);

  return <div ref={div} style={{ position: 'absolute', inset: 0 }} />;
};
