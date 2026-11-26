--liquibase formatted sql

--changeset ite4120:reports-92-showcase-handover context:showcase
--comment The invented reports that are accepted or further were handed to their units over X-Road
/*
 * The inbox and the X-Road member code are made from the recipient the same way as the
 * application makes them: the first word of the unit, and the place without accents.
 * The day of the handover is the day of the acceptance, so the sys columns are written here.
 */
select set_config('core.sys_columns_disabled', 'true', true);

insert into reports.handover (report_id, recipient_name, unit, inbox, channel, service, state, message_id,
                              sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by)
select h.report_id, h.recipient_name, h.unit, h.inbox, h.channel, h.service, h.state, h.message_id,
       'A', 1, h.handed_at, 'coordinator', h.handed_at, 'coordinator'
  from (
select r.id as report_id,
       r.recipient_name,
       case when r.recipient_kind = 'MUNICIPALITY' then split_part(r.recipient_name, ', ', 2) else r.recipient_name end as unit,
       lower(split_part(c.responsible_unit, ' ', 1)) || '@' || p.member || '.example' as inbox,
       'XROAD' as channel,
       'ee-test/GOV/' || p.member || '/reports-inbox/receiveReport' as service,
       case when r.status = 'ACCEPTED' and r.id % 2 = 0 then 'SENT' else 'OPENED' end as state,
       'ee-test-' || md5(r.reference_number) as message_id,
       coalesce((select min(s.sys_created_at) from reports.status_change s
                 where s.report_id = r.id and s.to_status = 'ACCEPTED' and s.sys_status = 'A'), r.sys_created_at) as handed_at
  from reports.report r
  join reports.category c on c.code = r.category_code
  cross join lateral (select trim(both '-' from regexp_replace(lower(translate(
             case when r.recipient_kind = 'MUNICIPALITY' then split_part(r.recipient_name, ', ', 1) else r.recipient_name end,
             'ÕÄÖÜŠŽõäöüšž', 'OAOUSZoaousz')), '[^a-z0-9]+', '-', 'g')) as member) p
 where r.status in ('ACCEPTED', 'IN_PROGRESS', 'RESOLVED')
   and r.recipient_kind <> 'UNDETERMINED'
   and r.sys_status = 'A'
   and not exists (select 1 from reports.handover x where x.report_id = r.id)
       ) h;
--
