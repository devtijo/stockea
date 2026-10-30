package com.stockea.inventario.domain;

import com.stockea.inventario.api.FraccionLote;
import com.stockea.inventario.api.LoteDisponible;
import com.stockea.inventario.api.ResultadoAsignacion;
import com.stockea.shared.domain.DomainException;
import com.stockea.shared.domain.ErrorDominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AsignadorFefo {
    public ResultadoAsignacion asignar(List<LoteDisponible> lotesOrdenados, BigDecimal cantidad) {
        if (lotesOrdenados == null) throw new DomainException(ErrorDominio.LOTES_REQUERIDOS);
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException(ErrorDominio.CANTIDAD_INVALIDA);
        }

        List<FraccionLote> fracciones = new ArrayList<>();
        BigDecimal pendiente = cantidad;

        for (LoteDisponible lote : lotesOrdenados) {
            if (pendiente.signum() == 0) break;

            BigDecimal tomar = pendiente.min(lote.cantidadDisponible());
            if (tomar.signum() > 0) {
                fracciones.add(new FraccionLote(lote.id(), tomar));
                pendiente = pendiente.subtract(tomar);
            }
        }
        return new ResultadoAsignacion(fracciones, pendiente);
    }
}