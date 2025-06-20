-- Migration: Add 'deleted' column to 'trains' table for soft delete support
ALTER TABLE trains ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;

