import type { ReactElement } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AnimalList } from './pages/AnimalList';
import { AnimalCreate } from './pages/AnimalCreate';
import { AnimalDetail } from './pages/AnimalDetail';
import { Shell } from './pages/reports-coordinator/Shell';

/**
 * Components that may be ABSENT from this checkout register their routes by
 * exporting `routes: ReactElement[]` (a list of <Route>s) from
 * `pages/<component>/routes.tsx`. The glob is resolved at build time and is
 * simply empty when no such folder exists — the lecturer's private library
 * module lives there, gitignored. Students: add your <Route>s below, like animals.
 */
const componentRoutes = Object.values(
  import.meta.glob<{ routes: ReactElement[] }>('./pages/*/routes.tsx', { eager: true }),
).flatMap((m) => m.routes);

export const App = () => {
  const { pathname } = useLocation();
  const routes = (
    <Routes>
      <Route path="/" element={<Navigate to="/spot" replace />} />
      <Route path="/animals" element={<AnimalList />} />
      <Route path="/animals/new" element={<AnimalCreate />} />
      <Route path="/animals/:id" element={<AnimalDetail />} />
      {componentRoutes}
    </Routes>
  );
  return /^\/(reports(-xroad)?|unit)(\/|$)/.test(pathname) ? <Shell>{routes}</Shell> : routes;
};
