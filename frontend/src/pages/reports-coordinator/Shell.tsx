import { useSyncExternalStore, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { ApiOutlined, EnvironmentOutlined, InboxOutlined, LogoutOutlined, MobileOutlined } from '@ant-design/icons';
import { logo } from '../reports/art';
import './work.css';

const UNIT_LINKS = [{ to: '/unit', end: false, icon: <InboxOutlined />, text: 'Handed to us' }];
const LINKS = [
  { to: '/reports', end: true, icon: <InboxOutlined />, text: 'Reports' },
  { to: '/reports/map', end: false, icon: <EnvironmentOutlined />, text: 'Map' },
  { to: '/reports-xroad', end: false, icon: <ApiOutlined />, text: 'X-Road' },
];
const signedIn = () => localStorage.getItem('reports_user') ?? '';

/**
 * The frame of the pages of a municipal worker: the coordinator, or a unit that solves the
 * reports handed to it. A web page only, there is no phone version of it.
 */
export const Shell = ({ children }: { children: ReactNode }) => {
  /* Signing in and out reloads the page, so nothing has to be watched. */
  const name = useSyncExternalStore(() => () => undefined, signedIn);
  const unitPage = window.location.pathname.startsWith('/unit');
  const user = unitPage ? name.endsWith('.example') : name === 'coordinator';
  return (
    <div className="work">
      <aside className="work-side">
        <div className="work-brand"><span dangerouslySetInnerHTML={{ __html: logo }} /><b>Public Space Reports</b></div>
        <nav aria-label="Main">
          {(unitPage ? UNIT_LINKS : LINKS).map((l) => <NavLink key={l.to} to={l.to} end={l.end}>{l.icon}<span>{l.text}</span></NavLink>)}
        </nav>
        <div className="work-foot">
          <a href="/spot"><MobileOutlined /><span>Resident pages</span></a>
          {user && (
            <button type="button" onClick={() => { localStorage.removeItem('reports_user'); window.location.assign(unitPage ? '/unit' : '/reports'); }}>
              <LogoutOutlined /><span>Sign out</span>
            </button>
          )}
          {user && <small>{unitPage ? `${name} (mock sign-in)` : 'Coordinator (mock sign-in)'}</small>}
        </div>
      </aside>
      <main className="work-main">{children}</main>
    </div>
  );
};
