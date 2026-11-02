import Keycloak from 'keycloak-js';


// Sesión con Keycloak (OIDC + PKCE). El token vive solo en memoria:
// no se guarda en localStorage (si hay XSS, no se lo pueden llevar).

const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL ?? `${window.location.origin}/auth`,
    realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'stockea',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT ?? 'stockea-web',
});

export type EstadoSesion = 'AUTENTICADO' | 'ANONIMO' | 'SIN_CONEXION';

let inicio: Promise<EstadoSesion> | null = null;
let keycloakListo = false;

/**
 * Revisa si ya hay sesión sin forzar el login (check-sso). Sin internet no se
 * contacta a Keycloak: el equipo sigue vendiendo con el perfil guardado y la
 * cola espera a que vuelva la conexión (ADR-001).
 */
export function iniciarSesion(): Promise<EstadoSesion> {
    // StrictMode monta dos veces: keycloak-js solo admite un init.
    inicio ??= (async () => {
        if (!navigator.onLine) return 'SIN_CONEXION';
        try {
            const ok = await keycloak.init({
                onLoad: 'check-sso',
                pkceMethod: 'S256',
                checkLoginIframe: false,
                silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
            });
            keycloakListo = true;
            return ok ? 'AUTENTICADO' : 'ANONIMO';
        } catch {
            return 'SIN_CONEXION'; // Keycloak caído: mismo trato que offline
        }
    })();
    return inicio;
}

export function entrar(): void {
    // Si la app abrió sin internet, keycloak-js no se inicializó: recargar lo inicializa.
    if (!keycloakListo) window.location.reload();
    else void keycloak.login();
}

export function salir(): void {
    if (!keycloakListo) window.location.reload();
    else void keycloak.logout({ redirectUri: window.location.origin });
}

/** Token vigente (se renueva si le quedan < 30 s). null = hay que iniciar sesión. */
export async function obtenerToken(): Promise<string | null> {
    if (!keycloak.authenticated) return null;
    try {
        await keycloak.updateToken(30);
        return keycloak.token ?? null;
    } catch {
        return null;
    }
}

/** fetch al backend con el token. Lanza SinSesionError si no hay sesión. */
export async function api(ruta: string, init: RequestInit = {}): Promise<Response> {
    const token = await obtenerToken();
    if (!token) throw new SinSesionError();
    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${token}`);
    if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    return fetch(ruta, { ...init, headers, signal: init.signal ?? AbortSignal.timeout(15_000) });
}

export class SinSesionError extends Error {
    constructor() {
        super('No hay sesión activa');
    }
}

/** Mensaje legible de un ProblemDetail del backend. */
export async function mensajeDeError(r: Response): Promise<string> {
    try {
        const p = (await r.json()) as { detail?: string };
        if (p.detail) return p.detail;
    } catch {
        /* cuerpo vacío */
    }
    if (r.status === 403) return 'No tiene permiso para esta acción.';
    return `Error ${r.status}`;
}