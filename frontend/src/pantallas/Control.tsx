import { useCallback, useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { api, mensajeDeError } from '../lib/auth';
import { db } from '../lib/db';
import { pesos } from '../lib/formato';

// Control del supervisor: discrepancias que dejaron las ventas y cuadre de cada turno de caja

interface Discrepancia {
    id: string;
    ventaId: string;
    productoId: string;
    motivo: 'STOCK_INSUFICIENTE' | 'PRECIO_DIFERENTE' | string;
    cantidadFaltante: number;
    precioCobrado: number | null;
    precioEsperado: number | null;
    detectadaEn: string;
}

interface Turno {
    id: string;
    cajero: string;
    estado: 'ABIERTO' | 'CERRADO';
    montoInicial: number;
    abiertoEn: string;
    cerradoEn: string | null;
    montoContado: number | null;
    efectivoEsperado: number | null;
    diferencia: number | null;
    ventas: { ventas: number; efectivo: number; transferencia: number; tarjeta: number; total: number };
}

const hora = (iso: string) => new Date(iso).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' });

function detalle(d: Discrepancia): string {
    if (d.motivo === 'STOCK_INSUFICIENTE') return `Se vendieron ${Number(d.cantidadFaltante)} sin stock registrado`;
    return `Cobrado ${pesos.format(Number(d.precioCobrado))}, debía ser ${pesos.format(Number(d.precioEsperado))}`;
}

async function pedir<T>(ruta: string): Promise<T> {
    const r = await api(ruta);
    if (!r.ok) throw new Error(await mensajeDeError(r));
    return (await r.json()) as T;
}

function pedirTodo() {
    return Promise.all([
        pedir<Discrepancia[]>('/api/discrepancias?estado=ABIERTA'),
        pedir<Turno[]>('/api/caja/turnos'),
    ]);
}

export default function Control({ online }: { online: boolean }) {
    const nombres = useLiveQuery(async () => new Map((await db.productos.toArray()).map((p) => [p.id, p.nombre])), [], new Map());
    const [discrepancias, setDiscrepancias] = useState<Discrepancia[]>([]);
    const [turnos, setTurnos] = useState<Turno[]>([]);
    const [resolviendo, setResolviendo] = useState<string | null>(null);
    const [nota, setNota] = useState('');
    const [error, setError] = useState<string | null>(null);

    const mostrar = useCallback(([d, t]: [Discrepancia[], Turno[]]) => {
        setDiscrepancias(d);
        setTurnos(t);
        setError(null);
    }, []);
    const fallo = (e: unknown) => setError(e instanceof Error ? e.message : 'No se pudo consultar.');
    const cargar = () => pedirTodo().then(mostrar, fallo);

    useEffect(() => {
        if (!online) return;
        let vigente = true;
        pedirTodo().then(
            (datos) => vigente && mostrar(datos),
            (e: unknown) => vigente && setError(e instanceof Error ? e.message : 'No se pudo consultar.'),
        );
        return () => {
            vigente = false;
        };
    }, [online, mostrar]);

    async function resolver(id: string) {
        const r = await api(`/api/discrepancias/${id}/resolver`, { method: 'POST', body: JSON.stringify({ nota: nota.trim() }) });
        if (!r.ok) {
            setError(await mensajeDeError(r));
            return;
        }
        setResolviendo(null);
        setNota('');
        await cargar();
    }

    if (!online) {
        return (
            <section className="tarjeta tarjeta--ancha">
                <h2>Control</h2>
                <p className="vacio">Este reporte necesita internet. La venta sigue funcionando sin conexión.</p>
            </section>
        );
    }

    return (
        <main className="rejilla rejilla--una">
            <section className="tarjeta" aria-labelledby="t-discrepancias">
                <div className="titulo-fila">
                    <h2 id="t-discrepancias">Discrepancias abiertas</h2>
                    <button type="button" className="texto" onClick={() => void cargar()}>Actualizar</button>
                </div>
                {error && <p className="alerta" role="alert">{error}</p>}
                {discrepancias.length === 0 ? (
                    <p className="vacio">No hay nada pendiente por revisar.</p>
                ) : (
                    <ul className="lista">
                        {discrepancias.map((d) => (
                            <li key={d.id} className="inventario">
                                <div className="producto">
                                    <div>
                                        <p className="producto__nombre">{nombres.get(d.productoId) ?? 'Producto'}</p>
                                        <p className="producto__meta">{detalle(d)} · {hora(d.detectadaEn)}</p>
                                    </div>
                                    <div className="producto__lado">
                                        <span className={`estado ${d.motivo === 'STOCK_INSUFICIENTE' ? 'estado--fallida' : 'estado--sincronizada_con_discrepancia'}`}>
                                            {d.motivo === 'STOCK_INSUFICIENTE' ? 'Sin stock' : 'Precio'}
                                        </span>
                                        {resolviendo !== d.id && (
                                            <button type="button" onClick={() => { setResolviendo(d.id); setNota(''); }}>Resolver</button>
                                        )}
                                    </div>
                                </div>
                                {resolviendo === d.id && (
                                    <div className="resolver">
                                        <label htmlFor={`nota-${d.id}`}>¿Qué se hizo?</label>
                                        <textarea id={`nota-${d.id}`} rows={2} maxLength={300} autoFocus value={nota}
                                                  placeholder="Ej.: conteo físico, había mercancía sin registrar"
                                                  onChange={(e) => setNota(e.target.value)} />
                                        <div className="acciones">
                                            <button type="button" className="texto" onClick={() => setResolviendo(null)}>Cancelar</button>
                                            <button type="button" className="primario" disabled={nota.trim() === ''}
                                                    onClick={() => void resolver(d.id)}>Marcar resuelta</button>
                                        </div>
                                    </div>
                                )}
                            </li>
                        ))}
                    </ul>
                )}
            </section>

            <section className="tarjeta" aria-labelledby="t-turnos">
                <h2 id="t-turnos">Turnos de caja</h2>
                {turnos.length === 0 ? (
                    <p className="vacio">Todavía no se ha abierto ninguna caja.</p>
                ) : (
                    <div className="envoltura-tabla">
                        <table>
                            <thead>
                            <tr>
                                <th>Cajero</th>
                                <th>Abierto</th>
                                <th className="num">Ventas</th>
                                <th className="num">Efectivo</th>
                                <th className="num">Otros medios</th>
                                <th className="num">Esperado</th>
                                <th className="num">Contado</th>
                                <th className="num">Diferencia</th>
                            </tr>
                            </thead>
                            <tbody>
                            {turnos.map((t) => {
                                const diferencia = t.diferencia == null ? null : Number(t.diferencia);
                                return (
                                    <tr key={t.id}>
                                        <td>{t.cajero}</td>
                                        <td>{hora(t.abiertoEn)}</td>
                                        <td className="num">{t.ventas.ventas}</td>
                                        <td className="num">{pesos.format(Number(t.ventas.efectivo))}</td>
                                        <td className="num">{pesos.format(Number(t.ventas.transferencia) + Number(t.ventas.tarjeta))}</td>
                                        <td className="num">
                                            {pesos.format(t.efectivoEsperado != null ? Number(t.efectivoEsperado)
                                                : Number(t.montoInicial) + Number(t.ventas.efectivo))}
                                        </td>
                                        <td className="num">{t.montoContado == null ? '—' : pesos.format(Number(t.montoContado))}</td>
                                        <td className="num">
                                            {t.estado === 'ABIERTO' ? (
                                                <span className="estado estado--pendiente_sync">Abierta</span>
                                            ) : (
                                                <span className={`estado ${diferencia === 0 ? 'estado--sincronizada' : 'estado--fallida'}`}>
                                                    {pesos.format(diferencia ?? 0)}
                                                </span>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </main>
    );
}
