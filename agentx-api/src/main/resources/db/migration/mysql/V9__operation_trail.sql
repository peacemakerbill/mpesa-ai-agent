-- A short log of what happened to each business request: asked, approved, sent, accepted, timed out, finished.
ALTER TABLE business_operations ADD COLUMN trail VARCHAR(4000);
