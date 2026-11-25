import type { Cat, SentReport } from './store';

/**
 * The invented past of the Smart-ID demo persons, the same rows as 91-showcase.sql. Used only
 * in development. A real resident has the history of the own device.
 */
const ROWS: Record<string, [ref: string, key: string, cat: Cat, address: string, minutesAgo: number, photo: boolean][]> = {
  'PNOEE-40504040001-DEM2-Q': [
    ['R-2026-5Y4G3B', 'signing-demo-1-08', 'road', "Pärnu maakond, Pärnu linn, Pärnu linn, Pardi tn 15", 1721, false],
    ['R-2026-KHZ5R2', 'signing-demo-1-07', 'graf', "Tartu maakond, Tartu linn, Tartu linn, Võru tänav T1", 10518, true],
    ['R-2026-VR6DF4', 'signing-demo-1-09', 'litter', "Lääne-Viru maakond, Rakvere linn, Posti tn 28", 18026, true],
    ['R-2026-WAWZM3', 'signing-demo-1-03', 'graf', "Harju maakond, Tallinn, Nõmme linnaosa, Paiste tn 12", 22693, true],
    ['R-2026-K3UH82', 'signing-demo-1-06', 'graf', "Harju maakond, Tallinn, Kesklinna linnaosa, Magasini tn 3j", 26131, true],
    ['R-2026-6G3QJ9', 'signing-demo-1-05', 'road', "Harju maakond, Tallinn, Lasnamäe linnaosa, Vahuri tn 2", 43357, false],
    ['R-2026-BPSUDQ', 'signing-demo-1-00', 'litter', "Harju maakond, Tallinn, Haabersti linnaosa, Õismäe tee 92", 51170, true],
    ['R-2026-YW3V94', 'signing-demo-1-04', 'litter', "Harju maakond, Tallinn, Kesklinna linnaosa, Pärnu mnt 62/1", 54627, true],
    ['R-2026-R5V2SM', 'signing-demo-1-01', 'light', "Viljandi maakond, Viljandi linn, Keskuse tn 4", 63056, false],
    ['R-2026-P97B3B', 'signing-demo-1-02', 'litter', "Tartu maakond, Tartu linn, Tartu linn, Lääne tn 64", 64686, false],
  ],
  'PNOEE-50001029996-DEMO-Q': [
    ['R-2026-KSVPVG', 'signing-demo-2-15', 'light', "Harju maakond, Tallinn, Kesklinna linnaosa, Uus-Tatari tn 23", 3857, true],
    ['R-2026-N8VA7N', 'signing-demo-2-16', 'litter', "Harju maakond, Tallinn, Lasnamäe linnaosa, Tähesaju tee 27", 8541, false],
    ['R-2026-7WSSF4', 'signing-demo-2-14', 'graf', "Harju maakond, Tallinn, Nõmme linnaosa, Sihi tn 98", 11313, true],
    ['R-2026-7436MX', 'signing-demo-2-17', 'litter', "Saare maakond, Saaremaa vald, Kuressaare linn, Transvaali tn 13", 25142, true],
    ['R-2026-Z6VYAY', 'signing-demo-2-12', 'road', "Lääne maakond, Haapsalu linn, Haapsalu linn, Loojangu tn 5", 29540, false],
    ['R-2026-NHVETB', 'signing-demo-2-13', 'road', "Harju maakond, Tallinn, Mustamäe linnaosa, A. H. Tammsaare tee 145", 33816, true],
    ['R-2026-JGYD9Z', 'signing-demo-2-10', 'light', "Pärnu maakond, Pärnu linn, Pärnu linn, Ringi tn 37", 44809, false],
    ['R-2026-8B9KEW', 'signing-demo-2-11', 'road', "Harju maakond, Tallinn, Põhja-Tallinna linnaosa, Kalaranna tänav T1", 45946, false],
  ],
  'PNOEE-39901012239-MOCK-Q': [
    ['R-2026-B2ZMMG', 'signing-demo-3-22', 'litter', "Harju maakond, Tallinn, Haabersti linnaosa, Paldiski mnt 118a", 5830, true],
    ['R-2026-SM5B9Z', 'signing-demo-3-20', 'litter', "Harju maakond, Tallinn, Pirita linnaosa, Merivälja tee T3", 15003, true],
    ['R-2026-F5UY7B', 'signing-demo-3-21', 'litter', "Harju maakond, Tallinn, Kesklinna linnaosa, Õllepruuli tn 6", 27419, false],
    ['R-2026-CYM8NV', 'signing-demo-3-23', 'tree', "Ida-Viru maakond, Narva linn, Karja tn 6", 34844, true],
    ['R-2026-UJ8PJ8', 'signing-demo-3-18', 'light', "Tartu maakond, Tartu linn, Tartu linn, Elva tn 13", 36647, true],
    ['R-2026-4YF6YH', 'signing-demo-3-19', 'road', "Tartu maakond, Tartu linn, Tartu linn, Karl Ernst von Baeri tänav T1", 43254, true],
  ],
  'PNOEE-30403039917-MOCK-Q': [
    ['R-2026-9SWAWN', 'signing-demo-4-25', 'light', "Lääne maakond, Haapsalu linn, Haapsalu linn, Posti tn 45", 1767, true],
    ['R-2026-CZBZ76', 'signing-demo-4-26', 'road', "Pärnu maakond, Pärnu linn, Pärnu linn, Pärnu jõgi L1", 28806, true],
    ['R-2026-JDJ39R', 'signing-demo-4-24', 'tree', "Harju maakond, Tallinn, Põhja-Tallinna linnaosa, Kalaranna tänav T1", 30747, true],
  ],
};

const ALL = new Set(Object.values(ROWS).flatMap((rows) => rows.map((r) => r[0])));
export const isDemo = (s: SentReport): boolean => ALL.has(s.ref);
export const demoHistory = (document: string): SentReport[] => (import.meta.env.DEV ? ROWS[document] ?? [] : []).map(([ref, key, cat, address, minutes, photo]) =>
  ({ ref, key, cat, address, at: new Date(Date.now() - minutes * 60_000).toISOString(), signed: true, photo, who: document }));
