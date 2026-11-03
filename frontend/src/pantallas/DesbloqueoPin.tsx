import { useState } from 'react';
import { verificarPin, type Perfil } from '../lib/db';

const INTENTOS_ANTES_DE_ESPERAR = 5;
const ESPERA_MS = 30_000;

// Al abrir la app SIN internet no hay Keycloak: el PIN de caja confirma que quien vende es el
// último usuario que inició sesión en este equipo
export default function DesbloqueoPin({ perfil, alDesbloquear }: { perfil: Perfil; alDesbloquear: () => void }) {
    const [pin, setPin] = useState('');
    const [fallos, setFallos] = useState(0);
    const [bloqueadoHasta, setBloqueadoHasta] = useState(0);
    const [error, setError] = useState<string | null>(null);
    const [verificando, setVerificando] = useState(false);

    async function entrar(e: React.FormEvent) {
        e.preventDefault();
        if (!perfil.pin || verificando) return;
        if (Date.now() < bloqueadoHasta) {
            setError('Demasiados intentos. Espere unos segundos.');
            return;
        }
        setVerificando(true);
        const ok = await verificarPin(pin, perfil.pin);
        setVerificando(false);
        if (ok) {
            alDesbloquear();
            return;
        }
        const total = fallos + 1;
        setFallos(total);
        setPin('');
        if (total % INTENTOS_ANTES_DE_ESPERAR === 0) {
            setBloqueadoHasta(Date.now() + ESPERA_MS);
            setError('Demasiados intentos. Espere 30 segundos.');
        } else {
            setError('PIN incorrecto.');
        }
    }

    return (
        <div className="pantalla-centro">
            <section className="tarjeta tarjeta--centro" aria-labelledby="t-pin">
                <h2 id="t-pin">Modo offline</h2>
                <p>
                    {perfil.negocio} · {perfil.nombre}. Ingrese su PIN de caja para seguir vendiendo sin internet.
                </p>
                {error && <p className="alerta" role="alert">{error}</p>}
                <form onSubmit={entrar} className="formulario">
                    <div className="campo">
                        <label htmlFor="pin">PIN</label>
                        <input id="pin" type="password" inputMode="numeric" pattern="\d{4,6}" maxLength={6} required
                               autoComplete="off" autoFocus value={pin} onChange={(e) => setPin(e.target.value)} />
                    </div>
                    <div className="acciones">
                        <button type="submit" className="primario" disabled={verificando}>
                            {verificando ? 'Verificando…' : 'Entrar'}
                        </button>
                    </div>
                </form>
                <p className="ayuda">¿Es otra persona? Conéctese a internet para iniciar sesión con su usuario.</p>
            </section>
        </div>
    );
}
