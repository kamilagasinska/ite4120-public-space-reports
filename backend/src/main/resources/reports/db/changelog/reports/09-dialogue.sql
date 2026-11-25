--liquibase formatted sql

--changeset ite4120:reports-09-accepted
--comment The status Accepted and publication on the map
alter table reports.report
    drop constraint report_status_chk,
    add constraint report_status_chk check (status in ('RECEIVED', 'ACCEPTED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    add column published boolean default false not null;

alter table reports.status_change
    drop constraint status_change_from_status_chk,
    drop constraint status_change_to_status_chk,
    add constraint status_change_from_status_chk check (from_status in ('RECEIVED', 'ACCEPTED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    add constraint status_change_to_status_chk check (to_status in ('RECEIVED', 'ACCEPTED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'));
--

--changeset ite4120:reports-09-message
--comment Updates of the coordinator, reminders and "the problem remains" of the sender
create table reports.message (
    id                  bigint default nextval('core.seq_id') not null,
    report_id           bigint not null,
    kind                text not null,
    text                text,
    expected_on         date,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint message_pk primary key (id),
    constraint message_report_fk foreign key (report_id) references reports.report (id),
    constraint message_kind_chk check (kind in ('UPDATE', 'REMINDER', 'REMAINS')),
    constraint message_text_chk check (kind = 'REMINDER' or (text is not null and char_length(btrim(text)) > 0)),
    constraint message_length_chk check (char_length(text) <= 500)
);

select core.create_table_metadata('reports.message');

create index message_report_idx on reports.message (report_id) where (sys_status = 'A');
--

--changeset ite4120:reports-09-photo
--comment Photos of a report, redrawn by the server
create table reports.photo (
    id                  bigint default nextval('core.seq_id') not null,
    report_id           bigint not null,
    kind                text not null,
    content             bytea not null,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint photo_pk primary key (id),
    constraint photo_report_fk foreign key (report_id) references reports.report (id),
    constraint photo_kind_chk check (kind in ('BEFORE', 'AFTER'))
);

select core.create_table_metadata('reports.photo');

create index photo_report_idx on reports.photo (report_id) where (sys_status = 'A');
--
