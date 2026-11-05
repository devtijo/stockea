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
    descuento: number; // pesos descontados en la línea (0 si no hay)
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
    turnoId: string;
}

export interface OperacionOutbox {
    operacionId: string;
    secuencia: number;
    tipo: 'VENTA' | 'ABRIR_TURNO' | 'CERRAR_TURNO';
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
    // Descuento del lote que FEFO venderá primero y cuántas unidades le quedan
    descuento: { porcentaje: number; unidades: number } | null;
}

// Turno de caja de ESTE equipo. Se abre y se cierra sin internet (ADR-025)
export interface TurnoLocal {
    id: string;
    cajeroId: string;
    cajero: string;
    montoInicial: number;
    abiertoEn: string;
    estado: 'ABIERTO' | 'CERRADO';
    cerradoEn?: string;
    montoContado?: number;
    efectivoEsperado?: number;
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
    // Material para verificar el PIN sin internet (PBKDF2, nunca el PIN en claro)
    pin: { sal: string; iteraciones: number; hash: string } | null;
}


// Base local. durability 'strict' = la transacción solo confirma cuando el navegador escribió a disco


export const db = new Dexie('stockea', { chromeTransactionDurability: 'strict' }) as Dexie & {
    ventas: EntityTable<VentaLocal, 'operacionId'>;
    outbox: EntityTable<OperacionOutbox, 'operacionId'>;
    meta: EntityTable<Meta, 'clave'>;
    productos: EntityTable<ProductoLocal, 'id'>;
    turnos: EntityTable<TurnoLocal, 'id'>;
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

db.version(3).stores({
    ventas: 'operacionId, secuencia, estado, turnoId',
    outbox: 'operacionId, secuencia, estado',
    meta: 'clave',
    productos: 'id, nombre',
    turnos: 'id, estado, abiertoEn',
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

// Cada operación de la cola lleva la siguiente secuencia del dispositivo.
// Se llama SIEMPRE dentro de una transacción que incluya meta y outbox.
async function encolar(tipo: OperacionOutbox['tipo'], operacionId: string, fechaCliente: string,
                       payload: object): Promise<number> {
    const actual = await db.meta.get('secuenciaDispositivo');
    const secuencia = Number(actual?.valor ?? '0') + 1;
    await db.meta.put({ clave: 'secuenciaDispositivo', valor: String(secuencia) });
    await db.outbox.add({
        operacionId,
        secuencia,
        tipo,
        fechaCliente,
        payloadJson: JSON.stringify(payload),
        estado: 'PENDIENTE',
        intentos: 0,
        proximoIntento: 0,
    });
    return secuencia;
}

// Descuento de una línea: solo las unidades que quedan en el lote con descuento.
// Si se venden más, el resto va a precio normal (el servidor lo verifica con FEFO).
export function descuentoLinea(producto: ProductoLocal | undefined, cantidad: number): number {
    if (!producto?.descuento) return 0;
    const unidades = Math.min(cantidad, producto.descuento.unidades);
    return Math.round((unidades * producto.precio * producto.descuento.porcentaje) / 100);
}

// Turnos de caja

export async function turnoAbierto(): Promise<TurnoLocal | null> {
    return (await db.turnos.where('estado').equals('ABIERTO').first()) ?? null;
}

export async function abrirTurnoLocal(perfil: Perfil, montoInicial: number): Promise<TurnoLocal> {
    if (!Number.isInteger(montoInicial) || montoInicial < 0) throw new Error('El monto inicial no es válido.');
    return db.transaction('rw', [db.turnos, db.outbox, db.meta], async () => {
        if (await turnoAbierto()) throw new Error('Ya hay una caja abierta en este equipo.');
        const turno: TurnoLocal = {
            id: uuidv7(),
            cajeroId: perfil.usuarioId,
            cajero: perfil.nombre,
            montoInicial,
            abiertoEn: new Date().toISOString(),
            estado: 'ABIERTO',
        };
        await db.turnos.add(turno);
        await encolar('ABRIR_TURNO', uuidv7(), turno.abiertoEn, {
            turnoId: turno.id,
            cajeroId: turno.cajeroId,
            montoInicial: String(montoInicial),
        });
        return turno;
    });
}

export interface ResumenTurnoLocal {
    ventas: number;
    efectivo: number;
    otros: number;
    efectivoEsperado: number;
}

// Lo que dice ESTE equipo. El cuadre oficial lo calcula el servidor al recibir el cierre.
export async function resumenTurno(turno: TurnoLocal): Promise<ResumenTurnoLocal> {
    const ventas = await db.ventas.where('turnoId').equals(turno.id)
        .filter((v) => v.estado !== 'RECHAZADA' && v.estado !== 'FALLIDA').toArray();
    const efectivo = ventas.filter((v) => v.metodoPago === 'EFECTIVO').reduce((s, v) => s + v.total, 0);
    const otros = ventas.filter((v) => v.metodoPago !== 'EFECTIVO').reduce((s, v) => s + v.total, 0);
    return { ventas: ventas.length, efectivo, otros, efectivoEsperado: turno.montoInicial + efectivo };
}

export async function cerrarTurnoLocal(turno: TurnoLocal, montoContado: number): Promise<TurnoLocal> {
    if (!Number.isInteger(montoContado) || montoContado < 0) throw new Error('El monto contado no es válido.');
    return db.transaction('rw', [db.turnos, db.ventas, db.outbox, db.meta], async () => {
        const resumen = await resumenTurno(turno);
        const cerrado: TurnoLocal = {
            ...turno,
            estado: 'CERRADO',
            cerradoEn: new Date().toISOString(),
            montoContado,
            efectivoEsperado: resumen.efectivoEsperado,
        };
        await db.turnos.put(cerrado);
        await encolar('CERRAR_TURNO', uuidv7(), cerrado.cerradoEn!, {
            turnoId: turno.id,
            montoContado: String(montoContado),
        });
        return cerrado;
    });
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

    const total = nueva.items.reduce((s, i) => s + i.cantidad * i.precioUnitario - i.descuento, 0);
    const montoRecibido = nueva.metodoPago === 'EFECTIVO' ? nueva.montoRecibido : total;
    if (montoRecibido < total) throw new Error('El monto recibido es menor que el total.');

    return db.transaction('rw', [db.ventas, db.outbox, db.meta, db.turnos], async () => {
        // Sin caja abierta no se vende: cada venta debe poder cuadrarse
        const turno = await turnoAbierto();
        if (!turno) throw new Error('Abra la caja antes de vender.');
        // El cajero es quien tiene la sesión en este equipo al momento de vender
        const perfil = await perfilGuardado();

        const operacionId = uuidv7();
        const fechaCliente = new Date().toISOString();

        // Payload exacto que lee ProcesadorVenta en el backend. Números como
        // string decimal: así nunca pasan por float.
        const payload = {
            turnoId: turno.id,
            cajeroId: perfil?.usuarioId ?? turno.cajeroId,
            metodoPago: nueva.metodoPago,
            montoRecibido: String(montoRecibido),
            totalCliente: String(total),
            items: nueva.items.map((i) => ({
                productoId: i.productoId,
                cantidad: String(i.cantidad),
                precioUnitario: String(i.precioUnitario),
                descuento: String(i.descuento),
            })),
        };
        const secuencia = await encolar('VENTA', operacionId, fechaCliente, payload);

        const venta: VentaLocal = {
            operacionId,
            secuencia,
            fechaCliente,
            metodoPago: nueva.metodoPago,
            items: nueva.items,
            total,
            montoRecibido,
            cambio: montoRecibido - total,
            estado: 'PENDIENTE_SYNC',
            turnoId: turno.id,
        };
        await db.ventas.add(venta);
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
    await db.transaction('rw', [db.ventas, db.outbox, db.meta, db.productos, db.turnos], async () => {
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
            await db.turnos.clear();
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

// PIN de caja sin internet: mismo PBKDF2-SHA256 que el backend (VerificadorPin)
export async function verificarPin(pin: string, material: NonNullable<Perfil['pin']>): Promise<boolean> {
    const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveBits']);
    const sal = Uint8Array.from(atob(material.sal), (c) => c.codePointAt(0) ?? 0);
    const bits = new Uint8Array(await crypto.subtle.deriveBits(
        { name: 'PBKDF2', hash: 'SHA-256', salt: sal, iterations: material.iteraciones }, base, 256));
    const esperado = Uint8Array.from(atob(material.hash), (c) => c.codePointAt(0) ?? 0);
    if (bits.length !== esperado.length) return false;
    let diferencia = 0;
    for (let i = 0; i < bits.length; i++) diferencia |= bits[i] ^ esperado[i];
    return diferencia === 0;
}
