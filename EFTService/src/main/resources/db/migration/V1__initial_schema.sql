-- EFTService: schema tối thiểu cho ddl-auto=validate
CREATE TABLE IF NOT EXISTS eft_placeholder (
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
