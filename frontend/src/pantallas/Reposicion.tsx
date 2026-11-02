import { useCallback, useEffect, useState } from 'react';
import { api, mensajeDeError } from '../lib/auth';

// Velocidad de venta por producto y sugerencia de reposición (ADR-006).
// Los datos los calcula el Worker a partir del evento VentaRegistrada (RabbitMQ):
// esta pantalla es la cara visible del message broker.

interface Velocidad {
    productoId: string;
    nombre: string;
    unidadesVendidas: number;
    velocidadDiaria: number;
    stock: number;
    diasCobertura: number | null;
    sugerencia: 'REPONER' | 'VIGILAR' | 'OK' | 'SIN_MOVIMIENTO';
}

const ETIQUETA: Record<Velocidad['sugerencia'], { texto: string; clase: string }> = {
    REPONER: { texto: 'Reponer', clase: 'estado--fallida' },
    VIGILAR: { texto: 'Vigilar', clase: 'estado--sincronizada_con_discrepancia' },
    OK: { texto: 'Bien', clase: 'estado--sincronizada' },
    SIN_MOVIMIENTO: { texto: 'Sin ventas', clase: 'estado--pendiente_sync' },
};

const DIAS = 7;

async function pedirVelocidad(): Promise<Velocidad[]> {
    const r = await api(`/api/analitica/velocidad?dias=${DIAS}`);
    if (!r.ok) throw new Error(await mensajeDeError(r));
    return (await r.json()) as Velocidad[];
}

const numero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });

export default function Reposicion({ online }: { online: boolean }) {
    const [filas, setFilas] = useState<Velocidad[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    const actualizar = useCallback(
        () =>
            pedirVelocidad().then(
                (datos) => {
                    setFilas(datos);
                    setError(null);
                },
                (e: unknown) => setError(e instanceof Error ? e.message : 'No se pudo consultar.'),
            ),
        [],
    );

    useEffect(() => {
        if (!online) return;
        let vigente = true;
        pedirVelocidad().then(
            (datos) => vigente && setFilas(datos),
            (e: unknown) => vigente && setError(e instanceof Error ? e.message : 'No se pudo consultar.'),
        );
        return () => {
            vigente = false;
        };
    }, [online]);

    if (!online) {
        return (
            <section className="tarjeta tarjeta--ancha">
                <h2>Reposición</h2>
                <p className="vacio">Este reporte necesita internet. La venta sigue funcionando sin conexión.</p>
            </section>
        );
    }

    const porReponer = filas?.filter((f) => f.sugerencia === 'REPONER').length ?? 0;

    return (
        <section className="tarjeta tarjeta--ancha" aria-labelledby="t-reposicion">
            <div className="titulo-fila">
                <h2 id="t-reposicion">Reposición · últimos {DIAS} días</h2>
                <button type="button" className="texto" onClick={() => void actualizar()}>
                    Actualizar
                </button>
            </div>
            <p className="ayuda">
                Velocidad = unidades vendidas por día. Cobertura = días que alcanza el stock a ese ritmo.
                {porReponer > 0 && ` Hay ${porReponer} producto(s) por reponer.`}
            </p>
            {error && <p className="alerta" role="alert">{error}</p>}
            {filas === null && !error && <p className="vacio">Cargando…</p>}
            {filas?.length === 0 && <p className="vacio">Aún no hay productos.</p>}
            {filas && filas.length > 0 && (
                <div className="envoltura-tabla">
                    <table>
                        <thead>
                        <tr>
                            <th>Producto</th>
                            <th className="num">Vendidas</th>
                            <th className="num">Por día</th>
                            <th className="num">Stock</th>
                            <th className="num">Cobertura</th>
                            <th>Sugerencia</th>
                        </tr>
                        </thead>
                        <tbody>
                        {filas.map((f) => (
                            <tr key={f.productoId}>
                                <td>{f.nombre}</td>
                                <td className="num">{numero.format(f.unidadesVendidas)}</td>
                                <td className="num">{numero.format(f.velocidadDiaria)}</td>
                                <td className="num">{numero.format(f.stock)}</td>
                                <td className="num">{f.diasCobertura === null ? '—' : `${numero.format(f.diasCobertura)} d`}</td>
                                <td>
                                    <span className={`estado ${ETIQUETA[f.sugerencia].clase}`}>{ETIQUETA[f.sugerencia].texto}</span>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}
