import { useEffect, useState } from 'react';
import {
    abrirTurnoLocal,
    cerrarTurnoLocal,
    resumenTurno,
    type Perfil,
    type ResumenTurnoLocal,
    type TurnoLocal,
} from '../lib/db';
import { solicitarSync } from '../SyncOutbox';
import { pesos } from '../lib/formato';

const soloNumeros = (texto: string) => Number(texto.replace(/\D/g, '')) || 0;

// Apertura de caja: el cajero cuenta la base con la que empieza y funciona sin internet
export function AbrirCaja({ perfil }: { perfil: Perfil }) {
    const [base, setBase] = useState('');
    const [error, setError] = useState<string | null>(null);

    async function abrir(e: React.FormEvent) {
        e.preventDefault();
        setError(null);
        try {
            await abrirTurnoLocal(perfil, soloNumeros(base));
            solicitarSync();
        } catch (e2) {
            setError(e2 instanceof Error ? e2.message : 'No se pudo abrir la caja.');
        }
    }

    return (
        <section className="tarjeta tarjeta--centro" aria-labelledby="t-abrir">
            <h2 id="t-abrir">Abrir caja</h2>
            <p>Cuente el efectivo con el que empieza el turno. Todas las ventas quedarán en esta caja hasta que la cierre.</p>
            {error && <p className="alerta" role="alert">{error}</p>}
            <form onSubmit={abrir} className="formulario">
                <div className="campo">
                    <label htmlFor="base">Base en efectivo</label>
                    <input id="base" inputMode="numeric" autoComplete="off" placeholder="0" value={base}
                           onChange={(e) => setBase(e.target.value)} />
                </div>
                <div className="acciones">
                    <button type="submit" className="primario">Abrir caja</button>
                </div>
            </form>
        </section>
    );
}

// Cierre: el equipo muestra lo que espera, el cajero cuenta y se ve la diferencia antes de confirmar
export function CerrarCaja({ turno, alTerminar }: { turno: TurnoLocal; alTerminar: () => void }) {
    const [resumen, setResumen] = useState<ResumenTurnoLocal | null>(null);
    const [contado, setContado] = useState('');
    const [cerrado, setCerrado] = useState<TurnoLocal | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        void resumenTurno(turno).then(setResumen);
    }, [turno]);

    const diferencia = resumen ? soloNumeros(contado) - resumen.efectivoEsperado : 0;

    async function cerrar() {
        setError(null);
        try {
            setCerrado(await cerrarTurnoLocal(turno, soloNumeros(contado)));
            solicitarSync();
        } catch (e) {
            setError(e instanceof Error ? e.message : 'No se pudo cerrar la caja.');
        }
    }

    return (
        <div className="modal-fondo">
            <div className="modal" role="dialog" aria-modal="true" aria-labelledby="t-cerrar">
                <h3 id="t-cerrar">{cerrado ? 'Caja cerrada' : 'Cerrar caja'}</h3>
                {resumen && (
                    <dl className="resumen">
                        <dt>Abierta</dt>
                        <dd>{new Date(turno.abiertoEn).toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' })}</dd>
                        <dt>Ventas</dt>
                        <dd>{resumen.ventas}</dd>
                        <dt>Base</dt>
                        <dd>{pesos.format(turno.montoInicial)}</dd>
                        <dt>Ventas en efectivo</dt>
                        <dd>{pesos.format(resumen.efectivo)}</dd>
                        <dt>Transferencia y tarjeta</dt>
                        <dd>{pesos.format(resumen.otros)}</dd>
                        <dt><strong>Efectivo esperado</strong></dt>
                        <dd><strong>{pesos.format(resumen.efectivoEsperado)}</strong></dd>
                    </dl>
                )}
                {error && <p className="alerta" role="alert">{error}</p>}
                {cerrado ? (
                    <>
                        <p>
                            Contado {pesos.format(cerrado.montoContado ?? 0)} ·{' '}
                            {diferencia === 0 ? 'cuadra exacto' : diferencia > 0
                                ? `sobran ${pesos.format(diferencia)}` : `faltan ${pesos.format(-diferencia)}`}.
                        </p>
                        <p className="ayuda">El cierre se envía al servidor con la cola; el supervisor ve el cuadre oficial.</p>
                        <div className="modal__acciones">
                            <button type="button" className="primario" autoFocus onClick={alTerminar}>Aceptar</button>
                        </div>
                    </>
                ) : (
                    <>
                        <div className="campo">
                            <label htmlFor="contado">Efectivo contado en caja</label>
                            <input id="contado" inputMode="numeric" autoComplete="off" placeholder="0" autoFocus
                                   value={contado} onChange={(e) => setContado(e.target.value)} />
                        </div>
                        {contado !== '' && resumen && (
                            <p className={diferencia === 0 ? 'exito' : 'aviso'}>
                                {diferencia === 0 ? 'Cuadra exacto.' : diferencia > 0
                                    ? `Sobran ${pesos.format(diferencia)}.` : `Faltan ${pesos.format(-diferencia)}.`}
                            </p>
                        )}
                        <div className="modal__acciones">
                            <button type="button" className="texto" onClick={alTerminar}>Seguir vendiendo</button>
                            <button type="button" className="primario" disabled={contado === ''} onClick={() => void cerrar()}>
                                Cerrar caja
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
