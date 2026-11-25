--liquibase formatted sql

--changeset ite4120:reports-08-category-on-map
--comment Not every category is shown on the public map
alter table reports.category add column on_map boolean default true not null;
--

--changeset ite4120:reports-08-categories-seed
--comment Nine categories
update reports.category set name = 'Road or street' where code = 'ROAD_DAMAGE';

insert into reports.category (code, name, responsible_unit, on_map) values
    ('LIGHTING',         'Street lighting',        'Lighting Department',     true),
    ('TREES',            'Trees and parks',        'Parks Department',        true),
    ('DANGEROUS_OBJECT', 'Dangerous object',       'Safety Department',       true),
    ('DEAD_ANIMAL',      'Dead animal',            'Environment Department',  true),
    ('VANDALISM',        'Graffiti and vandalism', 'Property Department',     true),
    ('PUBLIC_ORDER',     'Public order',           'Public Order Department', false),
    ('OTHER',            'Something else',         'Coordinator',             true)
on conflict do nothing;
--
