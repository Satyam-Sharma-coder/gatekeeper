INSERT INTO management_users (
    organization_id, role_id, username, password_hash, enabled, created_at, updated_at
)
SELECT NULL, roles.id, 'platform-admin',
       '$2a$12$8/9R3WWZ/T59MI1uzme1QeBX95WNVOJXzrEikcdJ0kq7joBS7U5xy',
       TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM roles WHERE roles.name = 'PLATFORM_ADMIN';


INSERT INTO management_users (
    organization_id, role_id, username, password_hash, enabled, created_at, updated_at
)
SELECT NULL, roles.id, 'gateway-service',
       '$2a$12$8/9R3WWZ/T59MI1uzme1QeBX95WNVOJXzrEikcdJ0kq7joBS7U5xy',
       TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM roles WHERE roles.name = 'GATEWAY_SERVICE';