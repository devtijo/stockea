import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, type EstadoVentaLocal, type OperacionOutbox } from './lib/db';


// La SyncOutbox es el ÚNICO código que habla con el backend
// Online y offline usan el mismo camino: la venta ya está en Dexie; esto solo
// drena la cola hacia POST /api/sync/operaciones.

const ENDPOINT = '/api/sync/operaciones';
const EVENTO_DRENAR = 'stockea:drenar';
const TAMANO_LOTE = 50;
const INTERVALO_MS = 10_000;

interface ResultadoServidor {
    operacionId: string;
    estado: string; // ACEPTADA | REASIGNADA | DISCREPANCIA | RECHAZADA
    resultadoId: string | null;
}

export type TipoDrenado =
    | 'SINCRONIZADO'
    | 'NADA'
    | 'OFFLINE'
    | 'ESPERANDO'
    | 'SERVIDOR_NO_RESPONDE'
    | 'REAUTENTICAR'
    | 'OCUPADO';

export interface ResultadoDrenado {
    tipo: TipoDrenado;
    mensaje: string;
}
export function solicitarSync(): void {
    window.dispatchEvent(new Event(EVENTO_DRENAR));
}

// política

type Accion = 'OK' | 'REINTENTAR' | 'REAUTENTICAR' | 'FALLIDA';

function clasificar(status: number | undefined): Accion {
    if (status === undefined) return 'REINTENTAR'; // red caída / timeout
    if (status === 200) return 'OK';
    if (status === 401) return 'REAUTENTICAR';
    if (status === 429 || status >= 500) return 'REINTENTAR';
    return 'FALLIDA'; // 400, 403, 422...
}

/** Backoff exponencial con jitter, tope 60 s. */
function espera(intentos: number): number {
    return Math.min(60_000, 1000 * 2 ** intentos) * (0.5 + Math.random() / 2);
}

function estadoLocal(estadoServidor: string): EstadoVentaLocal {
    if (estadoServidor === 'ACEPTADA' || estadoServidor === 'REASIGNADA') return 'SINCRONIZADA';
    if (estadoServidor === 'DISCREPANCIA') return 'SINCRONIZADA_CON_DISCREPANCIA';
    return 'RECHAZADA';
}

// envío

let drenandoEnEstaPestana = false;
let pausadoPorAutenticacion = false;

async function enviar(lote: OperacionOutbox[]): Promise<Accion> {
    const cuerpo = {
        operaciones: lote.map((op) => ({
            operacionId: op.operacionId,
            secuenciaDispositivo: op.secuencia,
            tipo: op.tipo,
            fechaCliente: op.fechaCliente,
            payloadJson: op.payloadJson,
        })),
    };

    let respuesta: Response;
    try {
        respuesta = await fetch(ENDPOINT, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(cuerpo),
            signal: AbortSignal.timeout(15_000),
        });
    } catch {
        await programarReintento(lote, 'Sin respuesta del servidor');
        return 'REINTENTAR';
    }

    const accion = clasificar(respuesta.status);

    if (accion === 'OK') {
        const datos = (await respuesta.json()) as { resultados: ResultadoServidor[] };
        await aplicarResultados(datos.resultados);
    } else if (accion === 'REINTENTAR') {
        await programarReintento(lote, `HTTP ${respuesta.status}`);
    } else if (accion === 'REAUTENTICAR') {
        pausadoPorAutenticacion = true; // nada se borra: la cola espera
    } else if (lote.length > 1) {
        // Un 4xx en un lote: se reenvía de a una para aislar la operación
        // envenenada; las demás no deben quedar bloqueadas
        for (const op of lote) await enviar([op]);
    } else {
        await db.outbox.update(lote[0].operacionId, {
            estado: 'FALLIDA',
            ultimoError: `HTTP ${respuesta.status}`,
        });
        await db.ventas.update(lote[0].operacionId, { estado: 'FALLIDA' });
    }
    return accion;
}

async function aplicarResultados(resultados: ResultadoServidor[]): Promise<void> {
    await db.transaction('rw', [db.outbox, db.ventas], async () => {
        for (const r of resultados) {
            await db.outbox.delete(r.operacionId);
            await db.ventas.update(r.operacionId, {
                estado: estadoLocal(r.estado),
                resultadoId: r.resultadoId ?? undefined,
            });
        }
    });
}

async function programarReintento(lote: OperacionOutbox[], error: string): Promise<void> {
    await db.transaction('rw', db.outbox, async () => {
        for (const op of lote) {
            const intentos = op.intentos + 1;
            await db.outbox.update(op.operacionId, {
                intentos,
                proximoIntento: Date.now() + espera(intentos),
                ultimoError: error,
            });
        }
    });
}

async function drenarCola(): Promise<ResultadoDrenado> {
    let enviadas = 0;
    for (let vuelta = 0; vuelta < 20; vuelta++) {
        const pendientes = await db.outbox.where('estado').equals('PENDIENTE').sortBy('secuencia');
        if (pendientes.length === 0) {
            return enviadas > 0
                ? { tipo: 'SINCRONIZADO', mensaje: `${enviadas} operación(es) sincronizada(s).` }
                : { tipo: 'NADA', mensaje: 'Todo sincronizado.' };
        }
        // Se respeta el orden: si la primera está en espera, todas esperan
        if (pendientes[0].proximoIntento > Date.now()) {
            return { tipo: 'ESPERANDO', mensaje: 'Reintentando en unos segundos...' };
        }
        const lote = pendientes.slice(0, TAMANO_LOTE);
        const accion = await enviar(lote);
        if (accion === 'REINTENTAR') {
            return { tipo: 'SERVIDOR_NO_RESPONDE', mensaje: 'El servidor no responde. Las ventas siguen guardadas.' };
        }
        if (accion === 'REAUTENTICAR') {
            return { tipo: 'REAUTENTICAR', mensaje: 'Sesión vencida. Inicie sesión de nuevo (la cola se conserva).' };
        }
        enviadas += lote.length;
    }
    return { tipo: 'SINCRONIZADO', mensaje: `${enviadas} operación(es) sincronizada(s).` };
}

export async function drenarOutbox(): Promise<ResultadoDrenado> {
    if (!navigator.onLine) {
        return { tipo: 'OFFLINE', mensaje: 'MODO OFFLINE: las ventas se guardan en este equipo.' };
    }
    if (pausadoPorAutenticacion) {
        return { tipo: 'REAUTENTICAR', mensaje: 'Sesión vencida. Inicie sesión de nuevo (la cola se conserva).' };
    }
    if (drenandoEnEstaPestana) return { tipo: 'OCUPADO', mensaje: 'Sincronizando...' };

    drenandoEnEstaPestana = true;
    try {
        // Web Locks: una sola pestaña drena a la vez (evita envíos duplicados).
        if (navigator.locks) {
            return await navigator.locks.request(
                'stockea-drenar-outbox',
                { ifAvailable: true },
                async (lock) =>
                    lock ? drenarCola() : { tipo: 'OCUPADO' as const, mensaje: 'Otra pestaña está sincronizando.' },
            );
        }
        return await drenarCola();
    } finally {
        drenandoEnEstaPestana = false;
    }
}

// hook

export interface EstadoSync {
    online: boolean;
    servidorResponde: boolean;
    pendientes: number;
    fallidas: number;
    mensaje: string;
    ultimaSync: Date | null;
}


// Dispara el drenado al montar, al volver la conexión, cada 10 s y cuando
// alguien llama solicitarSync(). `online` informa; no cambia cómo se vende.

export function useSyncOutbox(): EstadoSync {
    const [online, setOnline] = useState(navigator.onLine);
    const [servidorResponde, setServidorResponde] = useState(true);
    const [mensaje, setMensaje] = useState('Listo.');
    const [ultimaSync, setUltimaSync] = useState<Date | null>(null);

    const pendientes = useLiveQuery(() => db.outbox.where('estado').equals('PENDIENTE').count(), [], 0);
    const fallidas = useLiveQuery(() => db.outbox.where('estado').equals('FALLIDA').count(), [], 0);

    useEffect(() => {
        let vivo = true;

        const intentar = async () => {
            const r = await drenarOutbox();
            if (!vivo || r.tipo === 'OCUPADO') return;
            setMensaje(r.mensaje);
            setServidorResponde(r.tipo !== 'SERVIDOR_NO_RESPONDE');
            if (r.tipo === 'SINCRONIZADO') setUltimaSync(new Date());
        };
        const alConectar = () => {
            setOnline(true);
            void intentar();
        };
        const alDesconectar = () => {
            setOnline(false);
            setMensaje('MODO OFFLINE: las ventas se guardan en este equipo.');
        };
        const alSolicitar = () => void intentar();

        window.addEventListener('online', alConectar);
        window.addEventListener('offline', alDesconectar);
        window.addEventListener(EVENTO_DRENAR, alSolicitar);
        const reloj = window.setInterval(alSolicitar, INTERVALO_MS);
        void intentar();

        return () => {
            vivo = false;
            window.removeEventListener('online', alConectar);
            window.removeEventListener('offline', alDesconectar);
            window.removeEventListener(EVENTO_DRENAR, alSolicitar);
            window.clearInterval(reloj);
        };
    }, []);

    return { online, servidorResponde, pendientes, fallidas, mensaje, ultimaSync };
}
