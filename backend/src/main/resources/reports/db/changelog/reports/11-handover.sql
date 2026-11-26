--liquibase formatted sql

--changeset ite4120:reports-11-handover
--comment Every handover of a report to the unit that solves it, and its answer
create table reports.handover (
    id                  bigint default nextval('core.seq_id') not null,
    report_id           bigint not null,
    recipient_name      text not null,
    unit                text not null,
    inbox               text not null,
    channel             text not null,
    service             text,
    state               text not null,
    message_id          text,
    detail              text,
    return_reason       text,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint handover_pk primary key (id),
    constraint handover_report_fk foreign key (report_id) references reports.report (id),
    constraint handover_channel_chk check (channel in ('XROAD', 'OUTBOX')),
    constraint handover_state_chk check (state in ('SENT', 'OPENED', 'RETURNED', 'FAILED')),
    constraint handover_return_reason_chk check (state <> 'RETURNED' or (return_reason is not null and char_length(btrim(return_reason)) > 0)),
    constraint handover_length_chk check (char_length(return_reason) <= 500 and char_length(detail) <= 500)
);

select core.create_table_metadata('reports.handover');

create index handover_report_idx on reports.handover (report_id) where (sys_status = 'A');
create index handover_inbox_idx on reports.handover (inbox) where (sys_status = 'A');
--
