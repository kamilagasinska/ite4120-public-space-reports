--liquibase formatted sql

--changeset ite4120:reports-10-sighting
--comment Other devices that see the same problem
create table reports.sighting (
    id                  bigint default nextval('core.seq_id') not null,
    report_id           bigint not null,
    key_hash            text not null,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint sighting_pk primary key (id),
    constraint sighting_report_fk foreign key (report_id) references reports.report (id)
);

select core.create_table_metadata('reports.sighting');

create unique index sighting_once_ukey
    on reports.sighting (report_id, key_hash) where (sys_status = 'A');
--
