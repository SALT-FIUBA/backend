-- Migration: Add 'deleted' column to 'organims' table for soft delete support
ALTER TABLE public.organims ADD COLUMN IF NOT EXISTS deleted BOOLEAN DEFAULT FALSE;

