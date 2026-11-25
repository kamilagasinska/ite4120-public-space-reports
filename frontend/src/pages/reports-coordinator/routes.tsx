import { Route } from 'react-router-dom';
import { ReportList } from './ReportList';
import { ReportPage } from './ReportPage';

export const routes = [
  <Route key="reports" path="/reports" element={<ReportList />} />,
  <Route key="report" path="/reports/:referenceNumber" element={<ReportPage />} />,
];
