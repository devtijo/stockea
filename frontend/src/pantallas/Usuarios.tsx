import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, mensajeDeError } from '../lib/auth';
import { ETIQUETA_ROL } from '../lib/formato';
import type { Perfil, Rol } from '../lib/db';

// Administración de usuarios del negocio (solo ADMINISTRADOR, ADR-005).
// Es una operación de gestión: requiere internet (no pasa por la cola offline).

interface Usuario {
    id: string;
    tipoDocumento: string;
    numeroDocumento: string;
    nombre: string;
    correo: string | null;
    rol: Rol;
    activo: boolean;
}

async function pedirUsuarios(): Promise<Usuario[]> {
    const r = await api('/api/usuarios');
    if (!r.ok) throw new Error(await mensajeDeError(r));
    return (await r.json()) as Usuario[];
}

const textoError = (e: unknown) => (e instanceof Error ? e.message : 'No se pudo cargar la lista.');

const VACIO = { tipo: 'CC', numero: '', nombre: '', correo: '', rol: 'CAJERO' as Rol, clave: '', pin: '' };

export default function Usuarios({ perfil, online }: { perfil: Perfil; online: boolean }) {
    const [usuarios, setUsuarios] = useState<Usuario[]>([]);
    const [form, setForm] = useState(VACIO);
    const [error, setError] = useState<string | null>(null);
    const [aviso, setAviso] = useState<string | null>(null);
    const [enviando, setEnviando] = useState(false);
    const [versionForm, setVersionForm] = useState(0); // remonta el form: limpia el estado :user-invalid

    const cargar = useCallback(
        () => pedirUsuarios().then(setUsuarios, (e: unknown) => setError(textoError(e))),
        [],
    );

    useEffect(() => {
        if (!online) return;
        let vigente = true; // si el componente se desmonta, no se actualiza el estado
        pedirUsuarios().then(
            (lista) => vigente && setUsuarios(lista),
            (e: unknown) => vigente && setError(textoError(e)),
        );
        return () => {
            vigente = false;
        };
    }, [online]);

    async function crear(e: FormEvent) {
        e.preventDefault();
        setError(null);
        setAviso(null);
        setEnviando(true);
        try {
            const r = await api('/api/usuarios', {
                method: 'POST',
                body: JSON.stringify({
                    documento: { tipo: form.tipo, numero: form.numero.trim() },
                    nombre: form.nombre.trim(),
                    correo: form.correo.trim() || null,
                    rol: form.rol,
                    claveTemporal: form.clave,
                    pin: form.pin,
                }),
            });
            if (!r.ok) throw new Error(await mensajeDeError(r));
            const creado = (await r.json()) as Usuario;
            setAviso(
                `${creado.nombre} ya puede entrar con el usuario ${form.tipo.toLowerCase()}-${form.numero.trim().toLowerCase()} ` +
                'y la clave temporal; el sistema le pedirá cambiarla.',
            );
            setForm(VACIO);
            setVersionForm((v) => v + 1);
            await cargar();
        } catch (e2) {
            setError(e2 instanceof Error ? e2.message : 'No se pudo crear el usuario.');
        } finally {
            setEnviando(false);
        }
    }

    async function inhabilitar(u: Usuario) {
        if (!window.confirm(`¿Inhabilitar a ${u.nombre}? Ya no podrá iniciar sesión ni sincronizar.`)) return;
        setError(null);
        const r = await api(`/api/usuarios/${u.id}/inhabilitar`, { method: 'POST' });
        if (!r.ok) setError(await mensajeDeError(r));
        await cargar();
    }

    const cambiar = (campo: keyof typeof VACIO) => (e: { target: { value: string } }) =>
        setForm((f) => ({ ...f, [campo]: e.target.value }));

    if (!online) {
        return (
            <section className="tarjeta tarjeta--ancha">
                <h2>Usuarios</h2>
                <p className="vacio">La gestión de usuarios necesita internet. La venta sigue funcionando sin conexión.</p>
            </section>
        );
    }

    return (
        <main className="rejilla">
            <section className="tarjeta" aria-labelledby="t-usuarios">
                <h2 id="t-usuarios">Usuarios de {perfil.negocio}</h2>
                {error && <p className="alerta" role="alert">{error}</p>}
                <ul className="lista">
                    {usuarios.map((u) => (
                        <li key={u.id} className="producto">
                            <div>
                                <p className="producto__nombre">
                                    {u.nombre} {u.id === perfil.usuarioId && <span className="sub">(usted)</span>}
                                </p>
                                <p className="producto__meta">
                                    {u.tipoDocumento} {u.numeroDocumento} · {ETIQUETA_ROL[u.rol]}
                                </p>
                            </div>
                            <div className="producto__lado">
                                {u.activo ? (
                                    u.id !== perfil.usuarioId && (
                                        <button type="button" onClick={() => void inhabilitar(u)}>
                                            Inhabilitar
                                        </button>
                                    )
                                ) : (
                                    <span className="estado estado--fallida">Inhabilitado</span>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            </section>

            <section className="tarjeta" aria-labelledby="t-nuevo">
                <h2 id="t-nuevo">Nuevo usuario</h2>
                {aviso && <p className="exito" role="status">{aviso}</p>}
                <form key={versionForm} onSubmit={crear} className="formulario">
                    <div className="fila-campos">
                        <div className="campo">
                            <label htmlFor="u-tipo">Documento</label>
                            <select id="u-tipo" value={form.tipo} onChange={cambiar('tipo')}>
                                <option value="CC">CC</option>
                                <option value="CE">CE</option>
                                <option value="PPT">PPT</option>
                                <option value="PA">Pasaporte</option>
                            </select>
                        </div>
                        <div className="campo campo--crece">
                            <label htmlFor="u-numero">Número</label>
                            <input id="u-numero" required pattern="[0-9A-Za-z]{5,15}" inputMode="numeric" value={form.numero}
                                   onChange={cambiar('numero')} />
                        </div>
                    </div>
                    <div className="campo">
                        <label htmlFor="u-nombre">Nombre completo</label>
                        <input id="u-nombre" required maxLength={120} value={form.nombre} onChange={cambiar('nombre')} />
                    </div>
                    <div className="campo">
                        <label htmlFor="u-correo">Correo (opcional)</label>
                        <input id="u-correo" type="email" maxLength={160} value={form.correo} onChange={cambiar('correo')} />
                    </div>
                    <div className="campo">
                        <label htmlFor="u-rol">Rol</label>
                        <select id="u-rol" value={form.rol} onChange={cambiar('rol')}>
                            <option value="CAJERO">Cajero: vende y maneja su caja</option>
                            <option value="SUPERVISOR">Supervisor: además inventario y discrepancias</option>
                            <option value="ADMINISTRADOR">Administrador: todo, incluso usuarios</option>
                        </select>
                    </div>
                    <div className="fila-campos">
                        <div className="campo campo--crece">
                            <label htmlFor="u-clave">Clave temporal</label>
                            <input id="u-clave" type="password" required minLength={8} maxLength={64} autoComplete="new-password"
                                   value={form.clave} onChange={cambiar('clave')} />
                        </div>
                        <div className="campo">
                            <label htmlFor="u-pin">PIN de caja</label>
                            <input id="u-pin" type="password" required pattern="\d{4,6}" inputMode="numeric" maxLength={6}
                                   autoComplete="off" value={form.pin} onChange={cambiar('pin')} />
                        </div>
                    </div>
                    <p className="ayuda">Clave de mínimo 8 caracteres con al menos un número. PIN de 4 a 6 dígitos.</p>
                    <div className="acciones">
                        <button type="submit" className="primario" disabled={enviando}>
                            {enviando ? 'Creando…' : 'Crear usuario'}
                        </button>
                    </div>
                </form>
            </section>
        </main>
    );
}
