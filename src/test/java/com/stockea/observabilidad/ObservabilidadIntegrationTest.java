package com.stockea.observabilidad;

import com.stockea.IntegracionBase;
import com.stockea.shared.seguridad.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Métricas de negocio, eventos de seguridad (A09) y correlación de peticiones. */
class ObservabilidadIntegrationTest extends IntegracionBase {

    @Test
    @DisplayName("Una venta suma exactamente a las métricas de negocio de Prometheus")
    void metricasDeNegocio() throws Exception {
        var cajero = crearNegocioCon(Rol.CAJERO);
        var gaseosa = crearProducto(cajero, "Gaseosa", 2500);
        sembrarLote(cajero, gaseosa, 1);
        var antes = metricas();

        venderPorSync(cajero, gaseosa, 3, 2500); // hay 1: queda discrepancia

        var despues = metricas();
        // Los contadores se comparten con otras pruebas del mismo contexto: se mide la diferencia.
        assertThat(diferencia(antes, despues, "stockea_sync_operaciones_total", "estado=\"DISCREPANCIA\""))
                .isEqualTo(1.0);
        assertThat(diferencia(antes, despues, "stockea_ventas_total", "estado=\"DISCREPANCIA\"")).isEqualTo(1.0);
        assertThat(diferencia(antes, despues, "stockea_ventas_pesos_total", "metodo_pago=\"EFECTIVO\""))
                .isEqualTo(7500.0);
        assertThat(diferencia(antes, despues, "stockea_discrepancias_registradas_total", "motivo=")).isEqualTo(1.0);
    }

    private static double diferencia(String antes, String despues, String metrica, String etiqueta) {
        return valor(despues, metrica, etiqueta) - valor(antes, metrica, etiqueta);
    }

    private static double valor(String texto, String metrica, String etiqueta) {
        var m = java.util.regex.Pattern.compile(
                metrica + "\\{[^}]*" + java.util.regex.Pattern.quote(etiqueta) + "[^}]*\\} ([0-9.E]+)").matcher(texto);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    @Test
    @DisplayName("A09: los accesos rechazados se cuentan como eventos de seguridad")
    void eventosDeSeguridad() throws Exception {
        var antes = metricas();

        get("/api/catalogo", null);
        get("/api/catalogo", "token-falso");

        assertThat(diferencia(antes, metricas(), "stockea_seguridad_eventos_total", "tipo=\"no_autenticado\""))
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("El X-Request-Id del gateway se devuelve y uno inválido se reemplaza")
    void correlacion() throws Exception {
        var conId = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/catalogo"))
                .header("X-Request-Id", "a1b2c3d4e5f6").build(), HttpResponse.BodyHandlers.discarding());
        assertThat(conId.headers().firstValue("X-Request-Id")).hasValue("a1b2c3d4e5f6");

        var malicioso = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/catalogo"))
                .header("X-Request-Id", "x\" inyectado").build(), HttpResponse.BodyHandlers.discarding());
        assertThat(malicioso.headers().firstValue("X-Request-Id").orElseThrow()).doesNotContain("inyectado");
    }

    @Test
    @DisplayName("Las métricas NO se publican en el puerto de la API, solo en el de gestión")
    void metricasSoloEnPuertoInterno() throws Exception {
        assertThat(get("/actuator/prometheus", null).statusCode()).isIn(401, 403, 404);
        assertThat(metricas()).contains("jvm_memory_used_bytes");
    }
}
