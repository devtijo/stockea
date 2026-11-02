import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, dispositivoId, type EstadoVentaLocal, type OperacionOutbox } from './lib/db';
import { obtenerToken } from './lib/auth';

// La SyncOutbox es el ÚNICO código que habla con el backend
// Online y offline usan el mismo camino: la venta ya está en Dexie; esto solo
// drena la cola hacia POST /api/sync/operaciones.

const ENDPOINT = '/api/sync/operaciones';
const EVENTO_DRENAR = 'stockea:drenar';
const TAMANO_LOTE = 50;
const INTERVALO_MS = 10_000;

interface ResultadoServidor {
    operacionId: string;
    estado: string; // ACEPTADA | REASIGNADA | DISCREPANCIA | RECHAZADA_...
    resultadoId: string | null;
}

export type TipoDrenado =
    | 'SINCRONIZADO'
    | 'NADA'
    | 'OFFLINE'
    | 'ESPERANDO'
    | 'SERVIDOR_NO_RESPONDE'
    | 'REAUTENTICAR'
    | 'ACCESO_REVOCADO'
    | 'OCUPADO';

export interface ResultadoDrenado {
    tipo: TipoDrenado;
    mensaje: string;
}

// Pide un drenado inmediato (por ejemplo, justo después de cobrar)
export function solicitarSync(): void {
    window.dispatchEvent(new Event(EVENTO_DRENAR));
}

//política

type Accion = 'OK' | 'REINTENTAR' | 'REAUTENTICAR' | 'ACCESO_REVOCADO' | 'FALLIDA';


function clasificar(status: number | undefined): Accion {
    if (status === undefined) return 'REINTENTAR'; // red caída / timeout
    if (status === 200) return 'OK';
    if (status === 401) return 'REAUTENTICAR';
    if (status === 403) return 'ACCESO_REVOCADO'; // usuario inhabilitado o equipo bloqueado
    if (status === 429 || status >= 500) return 'REINTENTAR';
    return 'FALLIDA'; // 400, 403, 422...
}

// Backoff exponencial con jitter, tope 60 s.
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
let pausa: 'REAUTENTICAR' | 'ACCESO_REVOCADO' | null = null;

const MENSAJE_PAUSA = {
    REAUTENTICAR: 'Sesión vencida. Inicie sesión de nuevo (las ventas se conservan).',
    ACCESO_REVOCADO: 'Este usuario o equipo fue bloqueado. Un administrador debe iniciar sesión (las ventas se conservan).',
} as const;

// Tras un nuevo inicio de sesión, la cola vuelve a intentar
export function reanudarSync(): void {
    pausa = null;
    solicitarSync();
}

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

    const token = await obtenerToken();
    if (!token) {
        pausa = 'REAUTENTICAR';
        return 'REAUTENTICAR';
    }

    let respuesta: Response;
    try {
        respuesta = await fetch(ENDPOINT, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
                'X-Dispositivo-Id': await dispositivoId(),
            },
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
    } else if (accion === 'REAUTENTICAR' || accion === 'ACCESO_REVOCADO') {
        pausa = accion; // nada se borra: la cola espera a otra sesión
    } else if (lote.length > 1) {
        // Un 4xx en un lote: se reenvía de a una para aislar la operación
        // envenenada; las demás no deben quedar bloqueadas (ADR-022).
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
        // Se respeta el orden: si la primera está en espera, todas esperan.
        if (pendientes[0].proximoIntento > Date.now()) {
            return { tipo: 'ESPERANDO', mensaje: 'Reintentando en unos segundos...' };
        }
        const lote = pendientes.slice(0, TAMANO_LOTE);
        const accion = await enviar(lote);
        if (accion === 'REINTENTAR') {
            return { tipo: 'SERVIDOR_NO_RESPONDE', mensaje: 'El servidor no responde. Las ventas siguen guardadas.' };
        }
        if (accion === 'REAUTENTICAR' || accion === 'ACCESO_REVOCADO') {
            return { tipo: accion, mensaje: MENSAJE_PAUSA[accion] };
        }
        enviadas += lote.length;
    }
    return { tipo: 'SINCRONIZADO', mensaje: `${enviadas} operación(es) sincronizada(s).` };
}

export async function drenarOutbox(): Promise<ResultadoDrenado> {
    if (!navigator.onLine) {
        return { tipo: 'OFFLINE', mensaje: 'MODO OFFLINE: las ventas se guardan en este equipo.' };
    }
    if (pausa) return { tipo: pausa, mensaje: MENSAJE_PAUSA[pausa] };
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
    pausa: 'REAUTENTICAR' | 'ACCESO_REVOCADO' | null;
    pendientes: number;
    fallidas: number;
    mensaje: string;
    ultimaSync: Date | null;
}


//Dispara el drenado al montar, al volver la conexión, cada 10 s y cuando
// alguien llama solicitarSync(). `online` informa; no cambia cómo se vende
export function useSyncOutbox(): EstadoSync {
    const [online, setOnline] = useState(navigator.onLine);
    const [servidorResponde, setServidorResponde] = useState(true);
    const [pausaActual, setPausaActual] = useState<EstadoSync['pausa']>(null);
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
            setPausaActual(r.tipo === 'REAUTENTICAR' || r.tipo === 'ACCESO_REVOCADO' ? r.tipo : null);
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

    return { online, servidorResponde, pausa: pausaActual, pendientes, fallidas, mensaje, ultimaSync };
}
