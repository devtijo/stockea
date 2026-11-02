package com.stockea.shared.metricas;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


// Las métricas solo cuentan ventas que hicieron commit
public final class DespuesDelCommit {

    private DespuesDelCommit(){
    }

    public static void ejecutar(Runnable accion){
        if(TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit(){
                    accion.run();
                }
            });
        } else{
            accion.run();
        }
    }
}
