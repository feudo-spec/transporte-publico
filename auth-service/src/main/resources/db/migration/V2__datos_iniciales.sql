-- Roles del sistema
INSERT INTO rol (nombre, descripcion) VALUES
    ('USUARIO',       'Usuario de la tarjeta de transporte'),
    ('ADMINISTRADOR', 'Gestiona usuarios y beneficios'),
    ('VALIDADOR',     'Cliente del simulador de validadores de Metro y Metrobus');

-- Permisos
INSERT INTO permiso (codigo, descripcion) VALUES
    ('PERFIL_GESTIONAR',     'Consultar y actualizar el perfil propio'),
    ('TARJETA_CONSULTAR',    'Consultar tarjetas y saldo'),
    ('RECARGA_REALIZAR',     'Realizar recargas en linea'),
    ('HISTORIAL_CONSULTAR',  'Consultar historial de transacciones y pagos'),
    ('BENEFICIO_SOLICITAR',  'Solicitar beneficios o descuentos'),
    ('FAMILIA_ADMINISTRAR',  'Administrar la cuenta familiar'),
    ('USUARIO_ADMINISTRAR',  'Consultar y desactivar usuarios'),
    ('BENEFICIO_EVALUAR',    'Aprobar o rechazar solicitudes de beneficio'),
    ('TARJETA_REGISTRAR_USO','Reportar el uso de una tarjeta');

-- Permisos por rol
INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p
WHERE r.nombre = 'USUARIO'
  AND p.codigo IN ('PERFIL_GESTIONAR', 'TARJETA_CONSULTAR', 'RECARGA_REALIZAR',
                   'HISTORIAL_CONSULTAR', 'BENEFICIO_SOLICITAR', 'FAMILIA_ADMINISTRAR');

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p
WHERE r.nombre = 'ADMINISTRADOR'
  AND p.codigo IN ('USUARIO_ADMINISTRAR', 'BENEFICIO_EVALUAR');

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso FROM rol r, permiso p
WHERE r.nombre = 'VALIDADOR'
  AND p.codigo = 'TARJETA_REGISTRAR_USO';
