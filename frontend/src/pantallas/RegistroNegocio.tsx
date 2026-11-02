import { useState, type FormEvent } from 'react';
import { entrar, mensajeDeError } from '../lib/auth';

// Alta pública de un negocio con su administrador (POST /api/publico/negocios).
// Es lo primero que hace un tendero: sin esto no hay usuarios ni catálogo.

const VERSION_POLITICA = '2026-10';

const INICIAL = {
    negocio: '',
    tipoNegocio: 'TIENDA',
    ciudad: '',
    direccion: '',
    tipoDoc: 'CC',
    numero: '',
    nombre: '',
    correo: '',
    clave: '',
    pin: '',
    acepta: false,
};

export default function RegistroNegocio({ alVolver }: { alVolver: () => void }) {
    const [f, setF] = useState(INICIAL);
    const [error, setError] = useState<string | null>(null);
    const [usuarioCreado, setUsuarioCreado] = useState<string | null>(null);
    const [enviando, setEnviando] = useState(false);

    const cambiar = (campo: keyof typeof INICIAL) => (e: { target: { value: string } }) =>
        setF((x) => ({ ...x, [campo]: e.target.value }));

    async function registrar(e: FormEvent) {
        e.preventDefault();
        setError(null);
        setEnviando(true);
        try {
            const r = await fetch('/api/publico/negocios', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                signal: AbortSignal.timeout(20_000),
                body: JSON.stringify({
                    negocio: { nombre: f.negocio.trim(), tipo: f.tipoNegocio, ciudad: f.ciudad.trim(), direccion: f.direccion.trim() },
                    administrador: {
                        documento: { tipo: f.tipoDoc, numero: f.numero.trim() },
                        nombre: f.nombre.trim(),
                        correo: f.correo.trim() || null,
                        clave: f.clave,
                        pin: f.pin,
                    },
                    aceptaPolitica: f.acepta,
                    versionPolitica: VERSION_POLITICA,
                }),
            });
            if (!r.ok) throw new Error(await mensajeDeError(r));
            const datos = (await r.json()) as { usuario: string };
            setUsuarioCreado(datos.usuario);
        } catch (e2) {
            setError(
                e2 instanceof TypeError || (e2 instanceof DOMException && e2.name === 'TimeoutError')
                    ? 'No hay conexión con el servidor. El registro necesita internet.'
                    : e2 instanceof Error
                        ? e2.message
                        : 'No se pudo registrar.',
            );
        } finally {
            setEnviando(false);
        }
    }

    if (usuarioCreado) {
        return (
            <section className="tarjeta tarjeta--centro">
                <h2>¡Negocio registrado!</h2>
                <p>
                    Su usuario es <strong>{usuarioCreado}</strong> (tipo y número de documento). Entre con la clave que eligió.
                </p>
                <div className="acciones">
                    <button type="button" className="primario" onClick={entrar}>
                        Iniciar sesión
                    </button>
                </div>
            </section>
        );
    }

    return (
        <section className="tarjeta tarjeta--centro" aria-labelledby="t-registro">
            <h2 id="t-registro">Registrar mi negocio</h2>
            {error && <p className="alerta" role="alert">{error}</p>}
            <form onSubmit={registrar} className="formulario">
                <fieldset className="grupo">
                    <legend>El negocio</legend>
                    <div className="campo">
                        <label htmlFor="r-negocio">Nombre del negocio</label>
                        <input id="r-negocio" required maxLength={120} value={f.negocio} onChange={cambiar('negocio')} />
                    </div>
                    <div className="fila-campos">
                        <div className="campo">
                            <label htmlFor="r-tipo">Tipo</label>
                            <select id="r-tipo" value={f.tipoNegocio} onChange={cambiar('tipoNegocio')}>
                                <option value="TIENDA">Tienda de barrio</option>
                                <option value="MINIMERCADO">Minimercado</option>
                                <option value="PANADERIA">Panadería</option>
                                <option value="DROGUERIA">Droguería</option>
                                <option value="OTRO">Otro</option>
                            </select>
                        </div>
                        <div className="campo campo--crece">
                            <label htmlFor="r-ciudad">Ciudad</label>
                            <input id="r-ciudad" required maxLength={80} value={f.ciudad} onChange={cambiar('ciudad')} />
                        </div>
                    </div>
                    <div className="campo">
                        <label htmlFor="r-direccion">Dirección</label>
                        <input id="r-direccion" required maxLength={160} value={f.direccion} onChange={cambiar('direccion')} />
                    </div>
                </fieldset>

                <fieldset className="grupo">
                    <legend>Administrador</legend>
                    <div className="fila-campos">
                        <div className="campo">
                            <label htmlFor="r-tipodoc">Documento</label>
                            <select id="r-tipodoc" value={f.tipoDoc} onChange={cambiar('tipoDoc')}>
                                <option value="CC">CC</option>
                                <option value="CE">CE</option>
                                <option value="PPT">PPT</option>
                                <option value="PA">Pasaporte</option>
                            </select>
                        </div>
                        <div className="campo campo--crece">
                            <label htmlFor="r-numero">Número</label>
                            <input id="r-numero" required pattern="[0-9A-Za-z]{5,15}" value={f.numero} onChange={cambiar('numero')} />
                        </div>
                    </div>
                    <div className="campo">
                        <label htmlFor="r-nombre">Nombre completo</label>
                        <input id="r-nombre" required maxLength={120} value={f.nombre} onChange={cambiar('nombre')} />
                    </div>
                    <div className="campo">
                        <label htmlFor="r-correo">Correo (opcional)</label>
                        <input id="r-correo" type="email" maxLength={160} value={f.correo} onChange={cambiar('correo')} />
                    </div>
                    <div className="fila-campos">
                        <div className="campo campo--crece">
                            <label htmlFor="r-clave">Clave</label>
                            <input id="r-clave" type="password" required minLength={8} maxLength={64} autoComplete="new-password"
                                   value={f.clave} onChange={cambiar('clave')} />
                        </div>
                        <div className="campo">
                            <label htmlFor="r-pin">PIN de caja</label>
                            <input id="r-pin" type="password" required pattern="\d{4,6}" inputMode="numeric" maxLength={6}
                                   autoComplete="off" value={f.pin} onChange={cambiar('pin')} />
                        </div>
                    </div>
                    <p className="ayuda">Clave de mínimo 8 caracteres con al menos un número. PIN de 4 a 6 dígitos.</p>
                </fieldset>

                <label className="consentimiento">
                    <input type="checkbox" required checked={f.acepta} onChange={(e) => setF((x) => ({ ...x, acepta: e.target.checked }))} />
                    <span>
            Autorizo el tratamiento de mis datos personales para operar el sistema, según la Ley 1581 de 2012
            (versión {VERSION_POLITICA}). Puedo consultarlos, corregirlos o pedir su supresión.
          </span>
                </label>

                <div className="acciones">
                    <button type="button" className="texto" onClick={alVolver}>
                        Volver
                    </button>
                    <button type="submit" className="primario" disabled={enviando}>
                        {enviando ? 'Registrando…' : 'Registrar negocio'}
                    </button>
                </div>
            </form>
        </section>
    );
}
