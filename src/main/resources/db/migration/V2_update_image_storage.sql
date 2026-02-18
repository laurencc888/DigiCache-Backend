-- This script migrates the database schema to store image paths instead of image data.
-- WARNING: This is a destructive migration. All existing image data in the database will be lost.
-- Please back up your database before running this script.

-- Disable foreign key checks during migration
PRAGMA foreign_keys=off;

BEGIN TRANSACTION;

-- Migrate the 'images' table
-- Step 1: Create a new table with the desired schema (using image_path instead of image_data)
CREATE TABLE images_new (
    id TEXT PRIMARY KEY,
    box_id TEXT,
    image_path TEXT,
    content_type TEXT,
    created_at TIMESTAMP
);

-- Step 2: Copy the data from the old table to the new one.
-- Note that we are not copying the 'image_data' column.
-- Existing rows will have a NULL 'image_path'.
INSERT INTO images_new (id, box_id, content_type, created_at)
SELECT id, box_id, content_type, created_at FROM images;

-- Step 3: Drop the old 'images' table
DROP TABLE images;

-- Step 4: Rename the new table to 'images'
ALTER TABLE images_new RENAME TO images;


-- Migrate the 'background_images' table
-- Step 1: Create a new table with the desired schema
CREATE TABLE background_images_new (
    box_id TEXT PRIMARY KEY,
    image_path TEXT
);

-- Step 2: Copy relevant data from the old table.
-- 'image_data' is not copied.
INSERT INTO background_images_new (box_id)
SELECT box_id FROM background_images;

-- Step 3: Drop the old 'background_images' table
DROP TABLE background_images;

-- Step 4: Rename the new table to 'background_images'
ALTER TABLE background_images_new RENAME TO background_images;

COMMIT;

-- Re-enable foreign key checks
PRAGMA foreign_keys=on;
