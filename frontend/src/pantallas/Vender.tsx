import { useMemo, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, registrarVentaLocal, type EstadoVentaLocal, type ItemVentaLocal, type MetodoPago } from '../lib/db';
import { solicitarSync, type EstadoSync } from '../SyncOutbox';
import { pesos } from '../lib/formato';

const METODOS: { valor: MetodoPago; etiqueta: string }[] = [
    { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
    { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia' },
    { valor: 'TARJETA', etiqueta: 'Tarjeta' },
];


const ETIQUETA_ESTADO: Record<EstadoVentaLocal, string> = {
    PENDIENTE_SYNC: 'Pendiente',
    SINCRONIZADA: 'Sincronizada',
    SINCRONIZADA_CON_DISCREPANCIA: 'Revisar',
    RECHAZADA: 'Rechazada',
    FALLIDA: 'Fallida',
};

interface Dialogo {
    titulo: string;
    lineas: string[];
    tipo: 'info' | 'error';
}

interface Props {
    sync: EstadoSync;
    alActualizarCatalogo: () => void;
}

// Pantalla de venta. Funciona igual con o sin interne
export default function Vender({ sync, alActualizarCatalogo }: Props) {
    const catalogo = useLiveQuery(() => db.productos.orderBy('nombre').toArray(), [], []);
    const [carrito, setCarrito] = useState<Record<string, number>>({});
    const [metodoPago, setMetodoPago] = useState<MetodoPago>('EFECTIVO');
    const [recibido, setRecibido] = useState('');
    const [dialogo, setDialogo] = useState<Dialogo | null>(null);
    const [guardando, setGuardando] = useState(false);

    const ventasRecientes = useLiveQuery(() => db.ventas.orderBy('secuencia').reverse().limit(8).toArray(), [], []);

    const items: ItemVentaLocal[] = useMemo(
        () =>
            catalogo
                .filter((p) => (carrito[p.id] ?? 0) > 0)
                .map((p) => ({
                    productoId: p.id,
                    nombre: p.nombre,
                    cantidad: carrito[p.id],
                    precioUnitario: p.precio,
                })),
        [carrito, catalogo],
    );

    // ADR-002: se vende aunque falte stock; el servidor lo deja como discrepancia.
    const sinStock = items.filter((i) => i.cantidad > (catalogo.find((p) => p.id === i.productoId)?.stock ?? 0));

    const total = items.reduce((s, i) => s + i.cantidad * i.precioUnitario, 0);
    const recibidoNum = Number(recibido.replace(/\D/g, '')) || 0;
    const cambio = metodoPago === 'EFECTIVO' ? recibidoNum - total : 0;

    const cambiarCantidad = (productoId: string, delta: number) =>
        setCarrito((c) => {
            const nueva = Math.max(0, (c[productoId] ?? 0) + delta);
            const copia = { ...c };
            if (nueva === 0) delete copia[productoId];
            else copia[productoId] = nueva;
            return copia;
        });

    async function cobrar() {
        if (items.length === 0 || guardando) return;
        if (metodoPago === 'EFECTIVO' && recibidoNum < total) {
            setDialogo({ tipo: 'error', titulo: 'Monto insuficiente', lineas: ['El monto recibido es menor que el total.'] });
            return;
        }
        setGuardando(true);
        try {
            const venta = await registrarVentaLocal({ metodoPago, items, montoRecibido: recibidoNum });
            setCarrito({});
            setRecibido('');
            setDialogo({
                tipo: 'info',
                titulo: 'Venta registrada',
                lineas: [
                    `Venta n.º ${venta.secuencia} guardada en este equipo.`,
                    `Total ${pesos.format(venta.total)} · Cambio ${pesos.format(venta.cambio)}`,
                    sync.online ? 'Enviando al servidor…' : 'Modo offline: se enviará al volver la conexión.',
                ],
            });
            solicitarSync();
        } catch (e) {
            setDialogo({
                tipo: 'error',
                titulo: 'No se pudo registrar',
                lineas: [e instanceof Error ? e.message : 'No se pudo guardar la venta.'],
            });
        } finally {
            setGuardando(false);
        }
    }

    return (
        <>
            <main className="rejilla">
                <section className="tarjeta" aria-labelledby="t-catalogo">
                    <div className="titulo-fila">
                        <h2 id="t-catalogo">Catálogo</h2>
                        <button type="button" className="texto" onClick={alActualizarCatalogo} disabled={!sync.online}>
                            Actualizar
                        </button>
                    </div>
                    {catalogo.length === 0 && (
                        <p className="vacio">
                            Aún no hay productos con precio. Un administrador debe registrarlos y darles entrada de mercancía.
                        </p>
                    )}
                    <ul className="lista">
                        {catalogo.map((p) => (
                            <li key={p.id} className="producto">
                                <div>
                                    <p className="producto__nombre">{p.nombre}</p>
                                    <p className={`producto__meta${p.stock <= 0 ? ' producto__meta--agotado' : ''}`}>
                                        {p.stock > 0 ? `${p.stock} disponibles` : 'Sin stock'}
                                    </p>
                                </div>
                                <div className="producto__lado">
                                    <span className="monto">{pesos.format(p.precio)}</span>
                                    <button type="button" onClick={() => cambiarCantidad(p.id, 1)} aria-label={`Agregar ${p.nombre}`}>
                                        Agregar
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                </section>

                <section className="tarjeta tarjeta--carrito" aria-labelledby="t-carrito">
                    <h2 id="t-carrito">Venta actual</h2>

                    {items.length === 0 ? (
                        <p className="vacio">Agrega productos para empezar.</p>
                    ) : (
                        <div>
                            {items.map((i) => (
                                <div key={i.productoId} className="linea">
                                    <span className="linea__nombre">{i.nombre}</span>
                                    <span className="cantidad">
                    <button type="button" className="mini" aria-label={`Quitar una unidad de ${i.nombre}`}
                            onClick={() => cambiarCantidad(i.productoId, -1)}>−</button>
                    <span>{i.cantidad}</span>
                    <button type="button" className="mini" aria-label={`Agregar una unidad de ${i.nombre}`}
                            onClick={() => cambiarCantidad(i.productoId, 1)}>+</button>
                  </span>
                                    <span className="linea__subtotal monto">{pesos.format(i.cantidad * i.precioUnitario)}</span>
                                </div>
                            ))}
                        </div>
                    )}

                    {sinStock.length > 0 && (
                        <p className="aviso" role="note">
                            Stock insuficiente de {sinStock.map((i) => i.nombre).join(', ')}. La venta se registra igual y quedará
                            para revisión del supervisor.
                        </p>
                    )}

                    <div className="total">
                        <span className="total__etiqueta">Total</span>
                        <span className="total__valor">{pesos.format(total)}</span>
                    </div>

                    <fieldset className="pagos">
                        <legend>Método de pago</legend>
                        {METODOS.map((m) => (
                            <label key={m.valor} className="pago">
                                <input type="radio" name="metodo-pago" value={m.valor} checked={metodoPago === m.valor}
                                       onChange={() => setMetodoPago(m.valor)} />
                                {m.etiqueta}
                            </label>
                        ))}
                    </fieldset>

                    {metodoPago === 'EFECTIVO' && (
                        <>
                            <div className="campo">
                                <label htmlFor="recibido">Recibido</label>
                                <input id="recibido" inputMode="numeric" autoComplete="off" placeholder="0" value={recibido}
                                       onChange={(e) => setRecibido(e.target.value)} />
                            </div>
                            <p className="cambio">
                                <span>Cambio</span>
                                <strong>{cambio >= 0 ? pesos.format(cambio) : '—'}</strong>
                            </p>
                        </>
                    )}

                    <div className="acciones">
                        <button type="button" className="texto" onClick={() => setCarrito({})} disabled={items.length === 0}>
                            Cancelar
                        </button>
                        <button type="button" className="primario" onClick={cobrar} disabled={items.length === 0 || guardando}>
                            {guardando ? 'Guardando…' : 'Cobrar'}
                        </button>
                    </div>
                </section>

                <section className="tarjeta tarjeta--ancha" aria-labelledby="t-ventas">
                    <h2 id="t-ventas">Ventas recientes</h2>
                    {ventasRecientes.length === 0 ? (
                        <p className="vacio">Todavía no hay ventas.</p>
                    ) : (
                        <div className="envoltura-tabla">
                            <table>
                                <thead>
                                <tr>
                                    <th className="num">N.º</th>
                                    <th>Hora</th>
                                    <th>Pago</th>
                                    <th className="num">Total</th>
                                    <th>Estado</th>
                                </tr>
                                </thead>
                                <tbody>
                                {ventasRecientes.map((v) => (
                                    <tr key={v.operacionId}>
                                        <td className="num">{v.secuencia}</td>
                                        <td>{new Date(v.fechaCliente).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })}</td>
                                        <td>{METODOS.find((m) => m.valor === v.metodoPago)?.etiqueta}</td>
                                        <td className="num">{pesos.format(v.total)}</td>
                                        <td>
                                            <span className={`estado estado--${v.estado.toLowerCase()}`}>{ETIQUETA_ESTADO[v.estado]}</span>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            </main>

            {dialogo && (
                <div className="modal-fondo">
                    <div className={`modal modal--${dialogo.tipo}`} role="alertdialog" aria-modal="true" aria-labelledby="titulo-modal">
                        <h3 id="titulo-modal">{dialogo.titulo}</h3>
                        {dialogo.lineas.map((l) => (
                            <p key={l}>{l}</p>
                        ))}
                        <div className="modal__acciones">
                            <button type="button" className="primario" autoFocus onClick={() => setDialogo(null)}>
                                Aceptar
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
