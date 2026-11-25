--liquibase formatted sql

--changeset ite4120:reports-03-status-change
--comment History of status changes after a report is created
/* sys_created_at and sys_created_by say when and by whom the status changed.
   Creating a report writes no row here. */
create table reports.status_change (
    id                  bigint default nextval('core.seq_id') not null,
    report_id           bigint not null,
    from_status         text not null,
    to_status           text not null,
    reason              text,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint status_change_pk primary key (id),
    constraint status_change_report_fk foreign key (report_id) references reports.report (id),
    constraint status_change_from_status_chk check (from_status in ('RECEIVED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    constraint status_change_to_status_chk check (to_status in ('RECEIVED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    constraint status_change_reason_chk check (char_length(reason) <= 500),
    constraint status_change_rejected_reason_chk check (to_status <> 'REJECTED' or (reason is not null and char_length(btrim(reason)) > 0))
);

select core.create_table_metadata('reports.status_change');

create index status_change_report_idx on reports.status_change (report_id) where (sys_status = 'A');
--
