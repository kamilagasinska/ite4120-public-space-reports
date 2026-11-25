--liquibase formatted sql

--changeset ite4120:reports-05-report-identity
--comment Channel, reporter, municipality and limits of a report
/* Reports stored before this changeset count as guest reports. */
alter table reports.report
    add column municipality_code text,
    add column municipality_name text,
    add column channel text default 'GUEST' not null,
    add column reporter_id bigint,
    add column ip_hash text,
    add column signed_digest text,
    add constraint report_reporter_fk foreign key (reporter_id) references reports.reporter (id),
    add constraint report_channel_chk check (channel in ('GUEST', 'DEVICE', 'SIGNED')),
    add constraint report_guest_chk check (channel <> 'GUEST' or reporter_id is null),
    add constraint report_signed_chk check (channel <> 'SIGNED' or signed_digest is not null),
    add constraint report_municipality_code_chk check (char_length(municipality_code) <= 10),
    add constraint report_municipality_name_chk check (char_length(municipality_name) <= 100);

create index report_reporter_idx on reports.report (reporter_id) where (sys_status = 'A');

create index report_ip_idx on reports.report (ip_hash) where (sys_status = 'A');

create index report_municipality_idx on reports.report (municipality_code) where (sys_status = 'A');

alter table reports.status_change
    add column false_report boolean default false not null,
    add constraint status_change_false_report_chk check (not false_report or to_status = 'REJECTED');
--
