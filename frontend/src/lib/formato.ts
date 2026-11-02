export const pesos = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export const ETIQUETA_ROL = {
    CAJERO: 'Cajero',
    SUPERVISOR: 'Supervisor',
    ADMINISTRADOR: 'Administrador',
} as const;
