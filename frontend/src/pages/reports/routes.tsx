import { Navigate, Route } from 'react-router-dom';
import { ResidentApp } from './ResidentApp';

/** The resident on the phone. `/reports/new` and `/reports/lookup` of the specification lead here. */
export const routes = [
  <Route key="spot" path="/spot" element={<ResidentApp />} />,
  <Route key="spot-screen" path="/spot/:screen" element={<ResidentApp />} />,
  <Route key="spot-one" path="/spot/:screen/:id" element={<ResidentApp />} />,
  <Route key="new" path="/reports/new" element={<Navigate to="/spot" replace />} />,
];
