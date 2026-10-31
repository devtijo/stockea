package com.stockea.sync.api;

public interface ProcesadorOperacion {
    TipoOperacion tipo();
    ResultadoOperacion procesar(ComandoOperacion cmd);
}