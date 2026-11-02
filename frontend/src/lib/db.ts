import { Dexie, type EntityTable } from 'dexie';

// Tipos locales (espejo del contrato del backend; el dinero va en pesos enteros)

export type MetodoPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'TARJETA';

export type EstadoVentaLocal =
    | 'PENDIENTE_SYNC'
    | 'SINCRONIZADA'
    | 'SINCRONIZADA_CON_DISCREPANCIA'
    | 'RECHAZADA'
    | 'FALLIDA';

export interface ItemVentaLocal {
    productoId: string;
    nombre: string;
    cantidad: number; // unidades enteras
    precioUnitario: number; // pesos enteros
}

export interface VentaLocal {
    operacionId: string; // UUID v7 generado aquí (ADR-002)
    secuencia: number; // secuenciaDispositivo
    fechaCliente: string; // ISO-8601
    metodoPago: MetodoPago;
    items: ItemVentaLocal[];
    total: number;
    montoRecibido: number;
    cambio: number;
    estado: EstadoVentaLocal;
    resultadoId?: string; // id de la venta en el servidor
}

export interface OperacionOutbox {
    operacionId: string;
    secuencia: number;
    tipo: 'VENTA';
    fechaCliente: string;
    payloadJson: string; // el backend espera un STRING con el JSON serializado
    estado: 'PENDIENTE' | 'FALLIDA';
    intentos: number;
    proximoIntento: number; // epoch ms
    ultimoError?: string;
}

interface Meta {
    clave: string;
    valor: string;
}

// Producto del catálogo del negocio, descargado de GET /api/catalogo
export interface ProductoLocal {
    id: string;
    nombre: string;
    unidadVenta: string;
    precio: number; // pesos enteros
    stock: number; // referencia al momento de descargar; la verdad está en el servidor (ADR-002)
}

export type Rol = 'CAJERO' | 'SUPERVISOR' | 'ADMINISTRADOR';

// Lo que devuelve GET /api/yo. Se guarda para poder abrir la caja sin internet
export interface Perfil {
    usuarioId: string;
    nombre: string;
    rol: Rol;
    negocioId: string;
    negocio: string;
    zonaHoraria: string;
}


// Base local. durability 'strict' = la transacción solo confirma cuando el navegador escribió a disco


export const db = new Dexie('stockea', { chromeTransactionDurability: 'strict' }) as Dexie & {
    ventas: EntityTable<VentaLocal, 'operacionId'>;
    outbox: EntityTable<OperacionOutbox, 'operacionId'>;
    meta: EntityTable<Meta, 'clave'>;
    productos: EntityTable<ProductoLocal, 'id'>;
};

db.version(1).stores({
    ventas: 'operacionId, secuencia, estado',
    outbox: 'operacionId, secuencia, estado',
    meta: 'clave',
});

db.version(2).stores({
    ventas: 'operacionId, secuencia, estado',
    outbox: 'operacionId, secuencia, estado',
    meta: 'clave',
    productos: 'id, nombre',
});

if (typeof navigator !== 'undefined' && navigator.storage?.persist) {
    void navigator.storage.persist();
}

// UUID v7: 48 bits de tiempo + aleatorio. Ordenable por fecha, sin librerías.

export function uuidv7(): string {
    const b = new Uint8Array(16);
    crypto.getRandomValues(b);
    const ms = Date.now();
    for (let i = 0; i < 6; i++) {
        b[i] = Math.floor(ms / 2 ** (8 * (5 - i))) % 256;
    }
    b[6] = (b[6] & 0x0f) | 0x70; // versión 7
    b[8] = (b[8] & 0x3f) | 0x80; // variante RFC 4122
    const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

// Registrar venta: venta + outbox + secuencia en UNA sola transacción.
// O se guardan las tres cosas o ninguna

export interface NuevaVenta {
    metodoPago: MetodoPago;
    items: ItemVentaLocal[];
    montoRecibido: number;
}

export async function registrarVentaLocal(nueva: NuevaVenta): Promise<VentaLocal> {
    if (nueva.items.length === 0) throw new Error('El carrito está vacío.');

    const total = nueva.items.reduce((s, i) => s + i.cantidad * i.precioUnitario, 0);
    const montoRecibido = nueva.metodoPago === 'EFECTIVO' ? nueva.montoRecibido : total;
    if (montoRecibido < total) throw new Error('El monto recibido es menor que el total.');

    return db.transaction('rw', [db.ventas, db.outbox, db.meta], async () => {
        const actual = await db.meta.get('secuenciaDispositivo');
        const secuencia = Number(actual?.valor ?? '0') + 1;
        await db.meta.put({ clave: 'secuenciaDispositivo', valor: String(secuencia) });

        const venta: VentaLocal = {
            operacionId: uuidv7(),
            secuencia,
            fechaCliente: new Date().toISOString(),
            metodoPago: nueva.metodoPago,
            items: nueva.items,
            total,
            montoRecibido,
            cambio: montoRecibido - total,
            estado: 'PENDIENTE_SYNC',
        };

        // Payload exacto que lee ProcesadorVenta en el backend. Números como
        // string decimal: así nunca pasan por float.
        const payload = {
            metodoPago: venta.metodoPago,
            montoRecibido: String(venta.montoRecibido),
            totalCliente: String(venta.total),
            items: venta.items.map((i) => ({
                productoId: i.productoId,
                cantidad: String(i.cantidad),
                precioUnitario: String(i.precioUnitario),
            })),
        };

        await db.ventas.add(venta);
        await db.outbox.add({
            operacionId: venta.operacionId,
            secuencia,
            tipo: 'VENTA',
            fechaCliente: venta.fechaCliente,
            payloadJson: JSON.stringify(payload),
            estado: 'PENDIENTE',
            intentos: 0,
            proximoIntento: 0,
        });

        return venta;
    });
}

/** Identificador estable de este equipo (se crea la primera vez). */
export async function dispositivoId(): Promise<string> {
    const existente = await db.meta.get('dispositivoId');
    if (existente) return existente.valor;
    const nuevo = uuidv7();
    await db.meta.put({ clave: 'dispositivoId', valor: nuevo });
    return nuevo;
}

// Perfil y catálogo

export async function perfilGuardado(): Promise<Perfil | null> {
    const fila = await db.meta.get('perfil');
    return fila ? (JSON.parse(fila.valor) as Perfil) : null;
}

/**
 * Deja el equipo listo para el negocio que inició sesión. Si el equipo era de
 * OTRO negocio, se borran sus datos locales, salvo que tenga ventas sin enviar:
 * en ese caso se bloquea, porque esas ventas no se pueden mandar con otra cuenta.
 */
export async function prepararEquipoPara(perfil: Perfil): Promise<void> {
    await db.transaction('rw', [db.ventas, db.outbox, db.meta, db.productos], async () => {
        const anterior = await perfilGuardado();
        if (anterior && anterior.negocioId !== perfil.negocioId) {
            const sinEnviar = await db.outbox.count();
            if (sinEnviar > 0) {
                throw new Error(
                    `Este equipo tiene ${sinEnviar} venta(s) sin enviar de "${anterior.negocio}". ` +
                    'Inicie sesión con una cuenta de ese negocio para enviarlas primero.',
                );
            }
            await db.ventas.clear();
            await db.productos.clear();
        }
        await db.meta.put({ clave: 'perfil', valor: JSON.stringify(perfil) });
    });
}

export async function guardarCatalogo(productos: ProductoLocal[]): Promise<void> {
    await db.transaction('rw', db.productos, async () => {
        await db.productos.clear();
        await db.productos.bulkPut(productos);
    });
}
