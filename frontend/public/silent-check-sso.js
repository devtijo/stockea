// keycloak-js carga silent-check-sso.html en un iframe para saber si ya hay sesión.
parent.postMessage(location.href, location.origin);
