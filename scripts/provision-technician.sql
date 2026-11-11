-- Execute com acesso administrativo ao banco, após o cadastro normal da conta.
-- Exemplo: psql -v technician_email=tecnico@exemplo.org -f scripts/provision-technician.sql
UPDATE users SET role='TECHNICIAN', updated_at=CURRENT_TIMESTAMP
WHERE email=lower(:'technician_email') AND role='PRODUCER'
  AND NOT EXISTS (SELECT 1 FROM farms WHERE owner_user_id=users.id)
RETURNING id, email, role;
