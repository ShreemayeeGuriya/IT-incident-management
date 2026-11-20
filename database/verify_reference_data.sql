-- Read-only check for the category and service examples from the OOP project brief.
BEGIN TRANSACTION READ ONLY;

DO $$
DECLARE
    missing_categories TEXT;
    missing_services TEXT;
BEGIN
    SELECT string_agg(expected.name, ', ' ORDER BY expected.name)
    INTO missing_categories
    FROM (VALUES
        ('Hardware'), ('Software'), ('Network'), ('Database'), ('Cloud'),
        ('Security'), ('Authentication'), ('Application'), ('Storage')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1 FROM categories c WHERE c.category_name = expected.name
    );

    SELECT string_agg(expected.name, ', ' ORDER BY expected.name)
    INTO missing_services
    FROM (VALUES
        ('Cloud Applications'), ('Database Services'), ('Network Services'),
        ('Authentication Services'), ('Enterprise Applications'), ('Storage Services'),
        ('Communication Platforms'), ('Digital Services'), ('Payment Application'),
        ('Cloud Storage')
    ) AS expected(name)
    WHERE NOT EXISTS (
        SELECT 1 FROM services s WHERE s.service_name = expected.name
    );

    IF missing_categories IS NOT NULL OR missing_services IS NOT NULL THEN
        RAISE EXCEPTION 'Missing category rows: %. Missing service rows: %.',
            COALESCE(missing_categories, 'none'), COALESCE(missing_services, 'none');
    END IF;

    RAISE NOTICE 'All required incident categories and IT services are present.';
END;
$$;

ROLLBACK;
