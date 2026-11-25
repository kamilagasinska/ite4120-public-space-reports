--liquibase formatted sql

--changeset ite4120:reports-01-category
--comment Category reference table with the responsible municipal unit
/* Natural-key reference data without sys columns. The responsible unit lives
   here and is never copied to a report (REPORTS.01). */
create table reports.category (
    code                text not null,
    name                text not null,
    responsible_unit    text not null,
    constraint category_pk primary key (code),
    constraint category_responsible_unit_chk check (char_length(responsible_unit) <= 100)
);
--

--changeset ite4120:reports-01-category-seed
--comment Seed the category reference list
insert into reports.category (code, name, responsible_unit) values
    ('ROAD_DAMAGE', 'Road damage', 'Roads Department'),
    ('LITTER',      'Litter',      'Environment Department')
on conflict do nothing;
--
