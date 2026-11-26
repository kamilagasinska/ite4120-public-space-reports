import { Route } from 'react-router-dom';
import { ReportPage } from '../reports-coordinator/ReportPage';
import { UnitList } from './UnitList';

export const routes = [
  <Route key="unit" path="/unit" element={<UnitList />} />,
  <Route key="unit-report" path="/unit/:referenceNumber" element={<ReportPage role="unit" />} />,
];
