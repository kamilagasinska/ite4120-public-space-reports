--liquibase formatted sql

--changeset ite4120:reports-90-demo-data context:demo
--comment Demo rows for local development
insert into reports.report (reference_number, category_code, address, latitude, longitude, description, status) values
    ('R-2026-DEMO01', 'ROAD_DAMAGE', 'Ehitajate tee 5, Tallinn', 59.395100, 24.671900, 'Deep pothole in the right lane near the bus stop', 'RECEIVED'),
    ('R-2026-DEMO02', 'LITTER',      'Raja 15, Tallinn',         null,      null,      'Rubbish bags left next to the footpath',          'IN_PROGRESS');

insert into reports.status_change (report_id, from_status, to_status)
select r.id, 'RECEIVED', 'IN_PROGRESS'
from reports.report r
where r.reference_number = 'R-2026-DEMO02';
--
