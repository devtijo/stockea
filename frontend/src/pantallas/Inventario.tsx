import { useCallback, useEffect, useState } from 'react';
import { api, mensajeDeError } from '../lib/auth';
import { pesos } from '../lib/formato';

// Gestión de inventario del supervisor: productos, precios, entrada de mercancía y descuentos por lote.
// Necesita internet: son cambios que deben quedar en el servidor antes de que los cajeros vendan.

interface Lote {
    id: string;
    fechaVencimiento: string;
    cantidadInicial: number;
    cantidadDisponible: number;
    costoUnitario: number;
    estado: 'ACTIVO' | 'AGOTADO' | 'VENCIDO';
    descuentoId: string | null;
    descuentoPorcentaje: number | null;
}

interface Producto {
    id: string;
    nombre: string;
    categoria: string | null;
    unidadVenta: string;
    precio: number;
    activo: boolean;
    stock: number;
    lotes: Lote[];
}

type Edicion = { tipo: 'PRECIO'; productoId: string } | { tipo: 'DESCUENTO'; loteId: string } | null;

const NUEVO = { nombre: '', categoria: '', unidadVenta: 'UNIDAD', precio: '' };
const ENTRADA = { productoId: '', cantidad: '', fechaVencimiento: '', costoUnitario: '' };

const soloNumeros = (texto: string) => texto.replace(/\D/g, '');
const fecha = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short' });

async function pedirProductos(): Promise<Producto[]> {
    const r = await api('/api/inventario/productos');
    if (!r.ok) throw new Error(await mensajeDeError(r));
    const datos = (await r.json()) as Producto[];
    return datos.map((p) => ({ ...p, precio: Number(p.precio), stock: Number(p.stock) }));
}

export default function Inventario({ online, alCambiar }: { online: boolean; alCambiar: () => void }) {
    const [productos, setProductos] = useState<Producto[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [aviso, setAviso] = useState<string | null>(null);
    const [nuevo, setNuevo] = useState(NUEVO);
    const [entrada, setEntrada] = useState(ENTRADA);
    const [edicion, setEdicion] = useState<Edicion>(null);
    const [valorEdicion, setValorEdicion] = useState('');

    const cargar = useCallback(() => pedirProductos().then(setProductos), []);

    useEffect(() => {
        if (!online) return;
        let vigente = true;
        pedirProductos().then(
            (datos) => vigente && setProductos(datos),
            (e: unknown) => vigente && setError(e instanceof Error ? e.message : 'No se pudo consultar.'),
        );
        return () => {
            vigente = false;
        };
    }, [online]);

    // Ejecuta un cambio, recarga la lista y refresca el catálogo de la caja
    async function ejecutar(accion: () => Promise<Response>, exito: string, limpiar?: () => void) {
        setError(null);
        setAviso(null);
        try {
            const r = await accion();
            if (!r.ok) throw new Error(await mensajeDeError(r));
            limpiar?.();
            setEdicion(null);
            await cargar();
            alCambiar();
            setAviso(exito);
        } catch (e) {
            setError(e instanceof Error ? e.message : 'No se pudo guardar.');
        }
    }

    async function crearProducto(e: React.FormEvent) {
        e.preventDefault();
        await ejecutar(() => api('/api/inventario/productos', {
            method: 'POST',
            body: JSON.stringify({
                nombre: nuevo.nombre.trim(),
                categoria: nuevo.categoria.trim() || null,
                unidadVenta: nuevo.unidadVenta,
                precio: soloNumeros(nuevo.precio),
            }),
        }), `Producto "${nuevo.nombre.trim()}" creado. Ahora registre su entrada de mercancía.`, () => setNuevo(NUEVO));
    }

    async function recibir(e: React.FormEvent) {
        e.preventDefault();
        await ejecutar(() => api('/api/inventario/entradas', {
            method: 'POST',
            body: JSON.stringify({
                productoId: entrada.productoId,
                cantidad: soloNumeros(entrada.cantidad),
                fechaVencimiento: entrada.fechaVencimiento,
                costoUnitario: soloNumeros(entrada.costoUnitario) || '0',
            }),
        }), 'Mercancía recibida: el lote ya está disponible para vender.', () => setEntrada(ENTRADA));
    }

    function guardarEdicion() {
        if (!edicion) return;
        if (edicion.tipo === 'PRECIO') {
            void ejecutar(() => api(`/api/inventario/productos/${edicion.productoId}/precio`, {
                method: 'PUT',
                body: JSON.stringify({ precio: soloNumeros(valorEdicion) }),
            }), 'Precio actualizado. Las cajas lo reciben en su próxima sincronización.');
        } else {
            void ejecutar(() => api('/api/descuentos', {
                method: 'POST',
                body: JSON.stringify({ loteId: edicion.loteId, porcentaje: valorEdicion.replace(',', '.') }),
            }), 'Descuento aplicado al lote.');
        }
    }

    const editar = (e: Edicion, valor = '') => {
        setEdicion(e);
        setValorEdicion(valor);
    };

    if (!online) {
        return (
            <section className="tarjeta tarjeta--ancha">
                <h2>Inventario</h2>
                <p className="vacio">La gestión de inventario necesita internet. La venta sigue funcionando sin conexión.</p>
            </section>
        );
    }

    const editorEnLinea = (etiqueta: string) => (
        <span className="editor">
            <input aria-label={etiqueta} inputMode="decimal" value={valorEdicion}
                   onChange={(e) => setValorEdicion(e.target.value)}
                   onKeyDown={(e) => e.key === 'Enter' && guardarEdicion()} />
            <button type="button" className="primario" onClick={guardarEdicion}>Guardar</button>
            <button type="button" className="texto" onClick={() => setEdicion(null)}>Cancelar</button>
        </span>
    );

    return (
        <main className="rejilla">
            <section className="tarjeta" aria-labelledby="t-productos">
                <h2 id="t-productos">Productos</h2>
                {error && <p className="alerta" role="alert">{error}</p>}
                {aviso && <p className="exito" role="status">{aviso}</p>}
                {productos.length === 0 && <p className="vacio">Aún no hay productos. Cree el primero.</p>}
                <ul className="lista">
                    {productos.map((p) => (
                        <li key={p.id} className={`inventario${p.activo ? '' : ' inventario--inactivo'}`}>
                            <div className="producto">
                                <div>
                                    <p className="producto__nombre">{p.nombre}</p>
                                    <p className="producto__meta">
                                        {p.categoria ?? 'Sin categoría'} · {p.stock} disponibles{p.activo ? '' : ' · inactivo'}
                                    </p>
                                </div>
                                <div className="producto__lado">
                                    {edicion?.tipo === 'PRECIO' && edicion.productoId === p.id ? editorEnLinea(`Nuevo precio de ${p.nombre}`) : (
                                        <>
                                            <span className="monto">{pesos.format(p.precio)}</span>
                                            <button type="button" onClick={() => editar({ tipo: 'PRECIO', productoId: p.id }, String(p.precio))}>
                                                Precio
                                            </button>
                                            <button type="button" className="texto" onClick={() => void ejecutar(
                                                () => api(`/api/inventario/productos/${p.id}/${p.activo ? 'desactivar' : 'activar'}`, { method: 'POST' }),
                                                p.activo ? `${p.nombre} ya no aparece en caja.` : `${p.nombre} vuelve a la venta.`)}>
                                                {p.activo ? 'Desactivar' : 'Activar'}
                                            </button>
                                        </>
                                    )}
                                </div>
                            </div>
                            {p.lotes.length > 0 && (
                                <ul className="lotes">
                                    {p.lotes.map((l) => (
                                        <li key={l.id}>
                                            <span>
                                                Vence {fecha(l.fechaVencimiento)} · {Number(l.cantidadDisponible)} de {Number(l.cantidadInicial)}
                                                {l.estado === 'VENCIDO' && <span className="estado estado--fallida">Vencido</span>}
                                                {l.descuentoPorcentaje != null && (
                                                    <span className="estado estado--sincronizada_con_discrepancia">−{Number(l.descuentoPorcentaje)} %</span>
                                                )}
                                            </span>
                                            {edicion?.tipo === 'DESCUENTO' && edicion.loteId === l.id ? editorEnLinea('Porcentaje de descuento') : (
                                                l.estado !== 'VENCIDO' && (l.descuentoId ? (
                                                    <button type="button" className="texto" onClick={() => void ejecutar(
                                                        () => api(`/api/descuentos/${l.descuentoId}/desactivar`, { method: 'POST' }),
                                                        'Descuento retirado.')}>
                                                        Quitar descuento
                                                    </button>
                                                ) : (
                                                    <button type="button" className="texto" onClick={() => editar({ tipo: 'DESCUENTO', loteId: l.id })}>
                                                        Descuento
                                                    </button>
                                                ))
                                            )}
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </li>
                    ))}
                </ul>
            </section>

            <div className="columna">
                <section className="tarjeta" aria-labelledby="t-entrada">
                    <h2 id="t-entrada">Recibir mercancía</h2>
                    <form onSubmit={recibir} className="formulario">
                        <div className="campo">
                            <label htmlFor="e-producto">Producto</label>
                            <select id="e-producto" required value={entrada.productoId}
                                    onChange={(e) => setEntrada({ ...entrada, productoId: e.target.value })}>
                                <option value="">Seleccione…</option>
                                {productos.filter((p) => p.activo).map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                            </select>
                        </div>
                        <div className="fila-campos">
                            <div className="campo">
                                <label htmlFor="e-cantidad">Cantidad</label>
                                <input id="e-cantidad" required inputMode="numeric" value={entrada.cantidad}
                                       onChange={(e) => setEntrada({ ...entrada, cantidad: e.target.value })} />
                            </div>
                            <div className="campo campo--crece">
                                <label htmlFor="e-vence">Vence</label>
                                <input id="e-vence" type="date" required value={entrada.fechaVencimiento}
                                       onChange={(e) => setEntrada({ ...entrada, fechaVencimiento: e.target.value })} />
                            </div>
                        </div>
                        <div className="campo">
                            <label htmlFor="e-costo">Costo por unidad</label>
                            <input id="e-costo" inputMode="numeric" placeholder="0" value={entrada.costoUnitario}
                                   onChange={(e) => setEntrada({ ...entrada, costoUnitario: e.target.value })} />
                        </div>
                        <div className="acciones">
                            <button type="submit" className="primario">Registrar entrada</button>
                        </div>
                    </form>
                </section>

                <section className="tarjeta" aria-labelledby="t-nuevo-producto">
                    <h2 id="t-nuevo-producto">Nuevo producto</h2>
                    <form onSubmit={crearProducto} className="formulario">
                        <div className="campo">
                            <label htmlFor="p-nombre">Nombre</label>
                            <input id="p-nombre" required maxLength={120} value={nuevo.nombre}
                                   onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })} />
                        </div>
                        <div className="fila-campos">
                            <div className="campo campo--crece">
                                <label htmlFor="p-categoria">Categoría</label>
                                <input id="p-categoria" maxLength={60} value={nuevo.categoria}
                                       onChange={(e) => setNuevo({ ...nuevo, categoria: e.target.value })} />
                            </div>
                            <div className="campo">
                                <label htmlFor="p-unidad">Se vende por</label>
                                <select id="p-unidad" value={nuevo.unidadVenta}
                                        onChange={(e) => setNuevo({ ...nuevo, unidadVenta: e.target.value })}>
                                    <option value="UNIDAD">Unidad</option>
                                    <option value="KILOGRAMO">Kilogramo</option>
                                    <option value="LIBRA">Libra</option>
                                </select>
                            </div>
                        </div>
                        <div className="campo">
                            <label htmlFor="p-precio">Precio de venta</label>
                            <input id="p-precio" required inputMode="numeric" value={nuevo.precio}
                                   onChange={(e) => setNuevo({ ...nuevo, precio: e.target.value })} />
                        </div>
                        <div className="acciones">
                            <button type="submit" className="primario">Crear producto</button>
                        </div>
                    </form>
                </section>
            </div>
        </main>
    );
}
