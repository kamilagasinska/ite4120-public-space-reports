import type { ReportStatus, Standing } from '../../reportsApi';
import type { Lang } from './texts';

/** What this phone remembers. Nothing of it is sent except the device number and the keys. */
export interface Person { document: string; label: string; name?: string }
export interface SentReport { ref: string; key: string; cat: Cat; address: string; at: string; status?: ReportStatus; signed: boolean; photo: boolean }
export type Cat = 'road' | 'litter' | 'light' | 'tree' | 'graf' | 'items';
export type Way = 'signed' | 'device' | 'guest';

const PREFIX = 'hio-';

function read<T>(key: string, fallback: T): T {
  try {
    return (JSON.parse(localStorage.getItem(PREFIX + key) ?? 'null') as T) ?? fallback;
  } catch {
    return fallback;
  }
}

const write = (key: string, value: unknown) => localStorage.setItem(PREFIX + key, JSON.stringify(value));

export const memory = {
  lang: (): Lang => read<Lang>('lang', 'en'),
  setLang: (l: Lang) => write('lang', l),
  way: (): Way | null => read<Way | null>('way', null),
  setWay: (w: Way | null) => write('way', w),
  person: (): Person | null => read<Person | null>('person', null),
  setPerson: (p: Person | null) => write('person', p),
  standing: (): Standing | null => read<Standing | null>('standing', null),
  setStanding: (s: Standing | null) => write('standing', s),
  sent: (): SentReport[] => read<SentReport[]>('sent', []),
  setSent: (s: SentReport[]) => write('sent', s),
  /** Created when the resident has agreed to it, not before. */
  device: (): string => {
    let id = read<string>('device', '');
    if (!id) {
      id = crypto.randomUUID();
      write('device', id);
    }
    return id;
  },
  forgetDevice: () => localStorage.removeItem(PREFIX + 'device'),
};

export interface Draft {
  key: string;
  cat: Cat;
  address: string;
  spot?: { latitude: number; longitude: number };
  recipient?: string;
  what: string;
  other: string;
  size: string;
  words: string;
  photos: File[];
  contactWay: '' | 'mail' | 'call' | 'no';
  contact: string;
  anonymous: boolean;
}

export const newDraft = (cat: Cat): Draft => ({
  key: crypto.randomUUID(), cat, address: '', what: '', other: '', size: '', words: '', photos: [],
  contactWay: '', contact: '', anonymous: false,
});

export const CATS: Cat[] = ['road', 'litter', 'light', 'tree', 'graf', 'items'];
export const TILE: Record<Cat, string> = {
  road: 'Roads', litter: 'Litter', light: 'Lights', tree: 'Nature', graf: 'Graffiti, damage', items: 'Danger, other',
};
export const NAME: Record<Cat, string> = {
  road: 'Road damage', litter: 'Litter', light: 'Street light', tree: 'Nature', graf: 'Graffiti and damage', items: 'Danger or other',
};
export const QUESTION: Record<Cat, string> = {
  road: 'What is broken?', litter: 'What is lying there?', light: 'What is wrong with the light?',
  tree: 'What is wrong in nature?', graf: 'What is damaged?', items: 'What did you notice?',
};
export const ELSE = 'Something else';
export const ANSWERS: Record<Cat, [string, string][]> = {
  road: [['Pothole', 'hole'], ['Cracked road', 'crack'], ['Broken kerb', 'kerb'], ['Missing drain cover', 'drain']],
  litter: [['Rubbish bags', 'bag'], ['Broken glass', 'glass'], ['Overflowing bin', 'bin'], ['Large item', 'sofa']],
  light: [['Light is off', 'lamp'], ['Light flickers', 'flick'], ['Open cable', 'cable'], ['Bent pole', 'pole']],
  tree: [['Fallen tree', 'tree'], ['Hanging branch', 'branch'], ['Tall grass or bush', 'grass'], ['Dead animal', 'paw']],
  graf: [['Graffiti', 'spray'], ['Broken bench', 'bench'], ['Broken bus stop', 'bus'], ['Broken playground', 'swing']],
  items: [['Dangerous object', 'warn'], ['Public order', 'horn']],
};
export const SIZES: Record<Cat, string[]> = {
  road: ['Small', 'Big', 'Dangerous'], litter: ['A little', 'A lot', 'Dangerous'], light: ['One light', 'Many', 'Dangerous'],
  tree: ['Small', 'Big', 'Dangerous'], graf: ['Small', 'Big', 'Dangerous'], items: ['Small', 'Big', 'Dangerous'],
};
const CODE: Record<Cat, string> = {
  road: 'ROAD_DAMAGE', litter: 'LITTER', light: 'LIGHTING', tree: 'TREES', graf: 'VANDALISM', items: 'OTHER',
};
const OWN: Record<string, string> = { 'Dead animal': 'DEAD_ANIMAL', 'Dangerous object': 'DANGEROUS_OBJECT', 'Public order': 'PUBLIC_ORDER' };

/** Six pictures, nine categories. */
export const codeOf = (d: Pick<Draft, 'cat' | 'what'>): string => OWN[d.what] ?? CODE[d.cat];
export const catOf = (code: string): Cat =>
  ({ ROAD_DAMAGE: 'road', LITTER: 'litter', LIGHTING: 'light', TREES: 'tree', DEAD_ANIMAL: 'tree', VANDALISM: 'graf' } as Record<string, Cat>)[code] ?? 'items';

export const detailsOf = (d: Draft): string =>
  [d.what === ELSE ? d.other : d.what, d.size, d.words].map((x) => x.trim()).filter(Boolean).join(', ');
/** The category name first, so the text is never shorter than the rule of 10 characters. */
export const descriptionOf = (d: Draft): string => `${NAME[d.cat]}: ${detailsOf(d)}`;
