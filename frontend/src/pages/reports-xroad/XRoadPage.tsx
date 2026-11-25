import { useCallback, useEffect, useState } from 'react';
import { Alert } from 'antd';
import { AppButtonPrimary, AppCard, AppTag, FieldItem, useAppNotification } from '@helex/ui';
import { ApiProblem, reportsApi } from '../../reportsApi';
import { NoAccess } from '../reports-coordinator/shared';

type Status = Awaited<ReturnType<typeof reportsApi.xroadStatus>>;

/** A live call to the X-Road test environment, for the coordinator. A failure is shown, not hidden. */
export const XRoadPage = () => {
  const notify = useAppNotification();
  const [status, setStatus] = useState<Status | null>(null);
  const [busy, setBusy] = useState(true);
  const [denied, setDenied] = useState(false);

  const check = useCallback(() => {
    setBusy(true);
    reportsApi.xroadStatus().then(setStatus)
      .catch((e) => {
        if (e instanceof ApiProblem && e.status === 403) setDenied(true);
        else notify.error('Could not check X-Road', e.message);
      })
      .finally(() => setBusy(false));
  }, [notify]);
  useEffect(check, [check]);

  if (denied) return <NoAccess />;
  return (
    <AppCard title="X-Road test environment" loading={busy && !status} style={{ maxWidth: 720, margin: '24px auto' }}>
      {status && (
        <>
          <FieldItem label="Connection">{status.available ? <AppTag color="green">answers</AppTag> : <AppTag color="red">does not answer</AppTag>}</FieldItem>
          <FieldItem label="Security server">{status.securityServer}</FieldItem>
          <FieldItem label="Service">{status.service}</FieldItem>
          {status.messageId && <FieldItem label="X-Road message id">{status.messageId}</FieldItem>}
          {status.requestHash && (
            <FieldItem label="Request hash, made by the security server"><span style={{ overflowWrap: 'anywhere' }}>{status.requestHash}</span></FieldItem>
          )}
          {status.services && (
            <FieldItem label="Services it listed">
              {status.services.length ? status.services.map((s) => <AppTag key={s}>{s}</AppTag>) : 'none'}
            </FieldItem>
          )}
          {!status.available && (
            <Alert type="warning" showIcon style={{ margin: '16px 0' }} title="X-Road is not available"
              description={`${status.detail ?? ''} Reports are still accepted, the address stays unverified.`} />
          )}
        </>
      )}
      <AppButtonPrimary loading={busy} onClick={check} style={{ marginTop: 16 }}>Check again</AppButtonPrimary>
    </AppCard>
  );
};
