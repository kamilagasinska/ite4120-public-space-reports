--liquibase formatted sql

--changeset ite4120:reports-02-report
--comment The report submitted by a resident
/* The CHECK constraints repeat the service validation, so the rules of
   REPORTS.01 hold even for a row that bypasses the API. */
create table reports.report (
    id                  bigint default nextval('core.seq_id') not null,
    reference_number    text not null,
    category_code       text not null,
    address             text not null,
    address_code        text,
    address_verified    boolean default false not null,
    latitude            numeric(9,6),
    longitude           numeric(9,6),
    description         text not null,
    reporter_contact    text,
    status              text default 'RECEIVED' not null,
    submission_key      text,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint report_pk primary key (id),
    constraint report_category_fk foreign key (category_code) references reports.category (code),
    constraint report_address_chk check (char_length(btrim(address)) > 0 and char_length(address) <= 255),
    constraint report_address_code_chk check (char_length(address_code) <= 30),
    constraint report_map_point_chk check ((latitude is null and longitude is null) or (latitude is not null and longitude is not null)),
    constraint report_latitude_chk check (latitude between -90 and 90),
    constraint report_longitude_chk check (longitude between -180 and 180),
    constraint report_description_chk check (char_length(btrim(description)) >= 10 and char_length(description) <= 1000),
    constraint report_contact_chk check (char_length(reporter_contact) <= 255),
    constraint report_status_chk check (status in ('RECEIVED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),
    constraint report_submission_key_chk check (char_length(submission_key) <= 64)
);

select core.create_table_metadata('reports.report');

create unique index report_reference_number_ukey
    on reports.report (reference_number) where (sys_status = 'A');

/* A repeated submit carries the same key and finds the existing report. */
create unique index report_submission_key_ukey
    on reports.report (submission_key) where (sys_status = 'A');

create index report_category_idx on reports.report (category_code) where (sys_status = 'A');

create index report_status_idx on reports.report (status) where (sys_status = 'A');
--
