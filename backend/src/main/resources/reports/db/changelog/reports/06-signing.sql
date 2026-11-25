--liquibase formatted sql

--changeset ite4120:reports-06-signing
--comment A report waiting for its Smart-ID signature
/* The payload is cleared as soon as the signing ends in any way. */
create table reports.signing (
    id                  bigint default nextval('core.seq_id') not null,
    token               text not null,
    session_id          text not null,
    payload             jsonb,
    state               text default 'PENDING' not null,
    report_id           bigint,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint signing_pk primary key (id),
    constraint signing_report_fk foreign key (report_id) references reports.report (id),
    constraint signing_state_chk check (state in ('PENDING', 'SIGNED', 'REFUSED', 'EXPIRED', 'FAILED')),
    constraint signing_payload_chk check ((state = 'PENDING') = (payload is not null)),
    constraint signing_signed_chk check ((state = 'SIGNED') = (report_id is not null))
);

select core.create_table_metadata('reports.signing');

create unique index signing_token_ukey
    on reports.signing (token) where (sys_status = 'A');
--
