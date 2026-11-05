import { useCallback, useEffect, useState } from 'react';
import { api, entrar, iniciarSesion, mensajeDeError, salir } from './lib/auth';
import { guardarCatalogo, perfilGuardado, prepararEquipoPara, type Perfil, type ProductoLocal } from './lib/db';
import { ETIQUETA_ROL } from './lib/formato';
import { reanudarSync, useSyncOutbox, type EstadoSync } from './SyncOutbox';
import Control from './pantallas/Control';
import DesbloqueoPin from './pantallas/DesbloqueoPin';
import Inventario from './pantallas/Inventario';
import RegistroNegocio from './pantallas/RegistroNegocio';
import Reposicion from './pantallas/Reposicion';
import Usuarios from './pantallas/Usuarios';
import Vender from './pantallas/Vender';

type Fase =
    | { tipo: 'CARGANDO' }
    | { tipo: 'BIENVENIDA' }
    | { tipo: 'REGISTRO' }
    | { tipo: 'PIN'; perfil: Perfil }
    | { tipo: 'LISTO'; perfil: Perfil; sinConexion: boolean }
    | { tipo: 'BLOQUEADO'; mensaje: string };

type Pestana = 'VENDER' | 'INVENTARIO' | 'CONTROL' | 'REPOSICION' | 'USUARIOS';

/** Descarga el catálogo del negocio y lo deja en Dexie para vender sin internet. */
async function descargarCatalogo(): Promise<void> {
  const r = await api('/api/catalogo');
  if (!r.ok) return;
  const datos = (await r.json()) as ProductoLocal[];
  const productos: ProductoLocal[] = datos.map((p) => ({
    ...p,
    precio: Number(p.precio),
    stock: Number(p.stock),
    descuento: p.descuento
        ? { porcentaje: Number(p.descuento.porcentaje), unidades: Number(p.descuento.unidades) }
        : null,
  }));
  await guardarCatalogo(productos);
}

async function arrancar(): Promise<Fase> {
  const sesion = await iniciarSesion();

  if (sesion === 'SIN_CONEXION') {
    // Sin internet se sigue vendiendo con el último perfil de este equipo, previa verificación del PIN.
    const perfil = await perfilGuardado();
    if (!perfil) {
      return { tipo: 'BLOQUEADO', mensaje: 'La primera vez se necesita internet para iniciar sesión en este equipo.' };
    }
    return perfil.pin
        ? { tipo: 'PIN', perfil }
        : { tipo: 'BLOQUEADO', mensaje: 'Inicie sesión una vez con internet para poder vender sin conexión.' };
  }
  if (sesion === 'ANONIMO') return { tipo: 'BIENVENIDA' };

  const r = await api('/api/yo');
  if (!r.ok) return { tipo: 'BLOQUEADO', mensaje: await mensajeDeError(r) };
  const perfil = (await r.json()) as Perfil;
  try {
    await prepararEquipoPara(perfil);
  } catch (e) {
    return { tipo: 'BLOQUEADO', mensaje: e instanceof Error ? e.message : 'Equipo en uso por otro negocio.' };
  }
  await descargarCatalogo().catch(() => undefined); // si falla, queda el catálogo anterior
  reanudarSync();
  return { tipo: 'LISTO', perfil, sinConexion: false };
}

export default function App() {
  const [fase, setFase] = useState<Fase>({ tipo: 'CARGANDO' });
  const [pestana, setPestana] = useState<Pestana>('VENDER');

  useEffect(() => {
    void arrancar().then(setFase);
  }, []);

  const actualizarCatalogo = useCallback(() => void descargarCatalogo().catch(() => undefined), []);

  if (fase.tipo === 'CARGANDO') {
    return <div className="pantalla-centro"><p className="sub">Cargando…</p></div>;
  }
  if (fase.tipo === 'BIENVENIDA' || fase.tipo === 'REGISTRO') {
    return (
        <div className="app">
          <header className="cabecera">
            <div>
              <h1 className="marca">Stockea</h1>
              <p className="sub">Punto de venta para tiendas de barrio, también sin internet</p>
            </div>
          </header>
          {fase.tipo === 'REGISTRO' ? (
              <RegistroNegocio alVolver={() => setFase({ tipo: 'BIENVENIDA' })} />
          ) : (
              <section className="tarjeta tarjeta--centro">
                <h2>Bienvenido</h2>
                <p>Si su negocio ya usa Stockea, inicie sesión con su documento. Si es nuevo, regístrelo en un minuto.</p>
                <div className="acciones">
                  <button type="button" className="texto" onClick={() => setFase({ tipo: 'REGISTRO' })}>
                    Registrar mi negocio
                  </button>
                  <button type="button" className="primario" onClick={entrar}>
                    Iniciar sesión
                  </button>
                </div>
              </section>
          )}
        </div>
    );
  }
  if (fase.tipo === 'PIN') {
    return <DesbloqueoPin perfil={fase.perfil}
                          alDesbloquear={() => setFase({ tipo: 'LISTO', perfil: fase.perfil, sinConexion: true })} />;
  }
  if (fase.tipo === 'BLOQUEADO') {
    return (
        <div className="pantalla-centro">
          <section className="tarjeta tarjeta--centro">
            <h2>No se puede continuar</h2>
            <p>{fase.mensaje}</p>
            <div className="acciones">
              <button type="button" className="primario" onClick={salir}>
                Cambiar de usuario
              </button>
            </div>
          </section>
        </div>
    );
  }
  return <Caja perfil={fase.perfil} sinConexionAlAbrir={fase.sinConexion} pestana={pestana} setPestana={setPestana}
               actualizarCatalogo={actualizarCatalogo} />;
}

interface CajaProps {
  perfil: Perfil;
  sinConexionAlAbrir: boolean;
  pestana: Pestana;
  setPestana: (p: Pestana) => void;
  actualizarCatalogo: () => void;
}

const ETIQUETA_CONEXION = {
  online: 'En línea',
  'sin-servidor': 'En línea · servidor no responde',
  offline: 'Modo offline',
} as const;

// Pestañas por rol: el cajero solo vende (no ve la barra); supervisor y administrador ven gestión
function Pestanas({ perfil, pestana, setPestana }: Readonly<Pick<CajaProps, 'perfil' | 'pestana' | 'setPestana'>>) {
  const opciones: { valor: Pestana; texto: string }[] = [
    { valor: 'VENDER', texto: 'Vender' },
    { valor: 'INVENTARIO', texto: 'Inventario' },
    { valor: 'CONTROL', texto: 'Control' },
    { valor: 'REPOSICION', texto: 'Reposición' },
  ];
  if (perfil.rol === 'ADMINISTRADOR') opciones.push({ valor: 'USUARIOS', texto: 'Usuarios' });
  return (
      <nav className="pestanas" aria-label="Secciones">
        {opciones.map((o) => (
            <button key={o.valor} type="button" aria-current={pestana === o.valor ? 'page' : undefined}
                    onClick={() => setPestana(o.valor)}>
              {o.texto}
            </button>
        ))}
      </nav>
  );
}

// Aviso para volver a iniciar sesión: la cola no se envía sin token
function AvisoSesion({ pausa }: Readonly<{ pausa: EstadoSync['pausa'] }>) {
  const revocado = pausa === 'ACCESO_REVOCADO';
  return (
      <p className="aviso aviso--barra" role="alert">
        {revocado
            ? 'Este usuario o equipo fue bloqueado. Las ventas siguen guardadas; un administrador debe iniciar sesión.'
            : 'Inicie sesión para enviar las ventas guardadas.'}{' '}
        <button type="button" className="texto" onClick={revocado ? salir : entrar}>
          {revocado ? 'Cambiar de usuario' : 'Iniciar sesión'}
        </button>
      </p>
  );
}

function Contenido({ perfil, pestana, online, sync, actualizarCatalogo }: Readonly<{
  perfil: Perfil; pestana: Pestana; online: boolean; sync: EstadoSync; actualizarCatalogo: () => void;
}>) {
  const esSupervisor = perfil.rol !== 'CAJERO'; // roles acumulativos (ADR-005)
  if (pestana === 'USUARIOS' && perfil.rol === 'ADMINISTRADOR') return <Usuarios perfil={perfil} online={online} />;
  if (pestana === 'INVENTARIO' && esSupervisor) return <Inventario online={online} alCambiar={actualizarCatalogo} />;
  if (pestana === 'CONTROL' && esSupervisor) return <Control online={online} />;
  if (pestana === 'REPOSICION' && esSupervisor) return <Reposicion online={online} />;
  return <Vender perfil={perfil} sync={sync} alActualizarCatalogo={actualizarCatalogo} />;
}

function Caja({ perfil, sinConexionAlAbrir, pestana, setPestana, actualizarCatalogo }: Readonly<CajaProps>) {
  const sync = useSyncOutbox();

  // Tras cada sincronización el servidor ya descontó stock: se refresca la referencia local.
  useEffect(() => {
    if (sync.ultimaSync) actualizarCatalogo();
  }, [sync.ultimaSync, actualizarCatalogo]);

  // Abrió sin internet y volvió la conexión: hay que iniciar sesión para enviar la cola.
  const debeEntrar = (sinConexionAlAbrir && sync.online) || sync.pausa !== null;

  let estadoConexion: keyof typeof ETIQUETA_CONEXION = 'offline';
  if (sync.online) estadoConexion = sync.servidorResponde ? 'online' : 'sin-servidor';

  return (
      <div className="app">
        <header className="cabecera">
          <div>
            <h1 className="marca">Stockea</h1>
            <p className="sub">
              {perfil.negocio} · {perfil.nombre} ({ETIQUETA_ROL[perfil.rol]})
            </p>
          </div>
          <div className="cabecera__lado">
            <div className={`conexion conexion--${estadoConexion}`} role="status" aria-live="polite">
              <span className="punto" aria-hidden="true" />
              <strong>{ETIQUETA_CONEXION[estadoConexion]}</strong>
              <span className="conexion__detalle">
              {sync.pendientes} en cola{sync.fallidas > 0 ? ` · ${sync.fallidas} fallida(s)` : ''}
            </span>
            </div>
            <button type="button" className="texto" onClick={salir} disabled={!sync.online}
                    title={sync.online ? 'Cerrar sesión' : 'Sin internet no se puede cerrar sesión'}>
              Salir
            </button>
          </div>
        </header>

        {debeEntrar && sync.online && <AvisoSesion pausa={sync.pausa} />}

        {perfil.rol !== 'CAJERO' && <Pestanas perfil={perfil} pestana={pestana} setPestana={setPestana} />}

        <Contenido perfil={perfil} pestana={pestana} online={sync.online && !sinConexionAlAbrir} sync={sync}
                   actualizarCatalogo={actualizarCatalogo} />

        <footer className="pie-estado">
          <span>{sync.mensaje}</span>
          <span>Última sincronización: {sync.ultimaSync ? sync.ultimaSync.toLocaleTimeString('es-CO') : '—'}</span>
        </footer>
      </div>
  );
}
