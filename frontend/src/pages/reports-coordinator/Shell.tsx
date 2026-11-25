import { useSyncExternalStore, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { ApiOutlined, EnvironmentOutlined, InboxOutlined, LogoutOutlined, MobileOutlined } from '@ant-design/icons';
import { logo } from '../reports/art';
import './work.css';

const LINKS = [
  { to: '/reports', end: true, icon: <InboxOutlined />, text: 'Reports' },
  { to: '/reports/map', end: false, icon: <EnvironmentOutlined />, text: 'Map' },
  { to: '/reports-xroad', end: false, icon: <ApiOutlined />, text: 'X-Road' },
];
const signedIn = () => localStorage.getItem('reports_user') === 'coordinator';

/** The frame of the pages of a municipal worker. A web page only, there is no phone version of it. */
export const Shell = ({ children }: { children: ReactNode }) => {
  /* Signing in and out reloads the page, so nothing has to be watched. */
  const user = useSyncExternalStore(() => () => undefined, signedIn);
  return (
    <div className="work">
      <aside className="work-side">
        <div className="work-brand"><span dangerouslySetInnerHTML={{ __html: logo }} /><b>Public Space Reports</b></div>
        <nav aria-label="Main">
          {LINKS.map((l) => <NavLink key={l.to} to={l.to} end={l.end}>{l.icon}<span>{l.text}</span></NavLink>)}
        </nav>
        <div className="work-foot">
          <a href="/spot"><MobileOutlined /><span>Resident pages</span></a>
          {user && (
            <button type="button" onClick={() => { localStorage.removeItem('reports_user'); window.location.assign('/reports'); }}>
              <LogoutOutlined /><span>Sign out</span>
            </button>
          )}
          {user && <small>Coordinator (mock sign-in)</small>}
        </div>
      </aside>
      <main className="work-main">{children}</main>
    </div>
  );
};
