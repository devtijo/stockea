package com.stockea.shared.tiempo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
public class FechaNegocio {
    public static LocalDate calcular(Instant fechaCliente, Clock relojServidor, ZoneId zonaNegocio){
        Instant instanteReal = relojServidor.instant();
        Instant instanteElegido;
        if(fechaCliente.isBefore(instanteReal)){
            instanteElegido = fechaCliente;
        }
        else{
            instanteElegido = instanteReal;
        }

        return instanteElegido.atZone(zonaNegocio).toLocalDate();

    }
}
