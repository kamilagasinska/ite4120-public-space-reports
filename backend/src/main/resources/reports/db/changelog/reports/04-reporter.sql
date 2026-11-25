--liquibase formatted sql

--changeset ite4120:reports-04-reporter
--comment Who reports, known only by a keyed hash
/* key_hash is an HMAC of the device identifier or of the Smart-ID person. The
   identifier itself is never stored. */
create table reports.reporter (
    id                  bigint default nextval('core.seq_id') not null,
    kind                text not null,
    key_hash            text not null,
    last_report_at      timestamptz not null,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint reporter_pk primary key (id),
    constraint reporter_kind_chk check (kind in ('DEVICE', 'SIGNED'))
);

select core.create_table_metadata('reports.reporter');

create unique index reporter_key_ukey
    on reports.reporter (kind, key_hash) where (sys_status = 'A');
--
