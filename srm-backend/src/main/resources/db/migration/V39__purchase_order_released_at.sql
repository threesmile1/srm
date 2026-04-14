alter table purchase_order
    add column released_at datetime(6) null;

create index idx_po_released_at on purchase_order (released_at);

