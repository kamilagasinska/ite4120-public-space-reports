--liquibase formatted sql

--changeset ite4120:reports-schema
--comment Create the reports schema
create schema if not exists reports;
--
