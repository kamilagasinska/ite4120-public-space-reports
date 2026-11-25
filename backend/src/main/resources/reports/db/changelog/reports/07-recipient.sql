--liquibase formatted sql

--changeset ite4120:reports-07-recipient
--comment Who a report is sent to, and every forwarding
alter table reports.report
    add column recipient_kind text default 'UNDETERMINED' not null,
    add column recipient_name text default 'Coordinator decides' not null,
    add constraint report_recipient_kind_chk check (recipient_kind in ('STATE_ROADS', 'MUNICIPALITY', 'OTHER', 'UNDETERMINED')),
    add constraint report_recipient_name_chk check (char_length(btrim(recipient_name)) > 0 and char_length(recipient_name) <= 220);

create table reports.forwarding (
    id                  bigint default nextval('core.seq_id') not null,
    report_id           bigint not null,
    from_name           text not null,
    to_kind             text not null,
    to_name             text not null,
    reason              text not null,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint forwarding_pk primary key (id),
    constraint forwarding_report_fk foreign key (report_id) references reports.report (id),
    constraint forwarding_to_kind_chk check (to_kind in ('STATE_ROADS', 'MUNICIPALITY', 'OTHER')),
    constraint forwarding_reason_chk check (char_length(btrim(reason)) > 0 and char_length(reason) <= 500)
);

select core.create_table_metadata('reports.forwarding');

create index forwarding_report_idx on reports.forwarding (report_id) where (sys_status = 'A');
--
