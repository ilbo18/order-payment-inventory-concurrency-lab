alter table inventories
    add column version bigint not null default 0;
