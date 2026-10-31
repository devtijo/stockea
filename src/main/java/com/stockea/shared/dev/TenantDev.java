package com.stockea.shared.dev;

import java.util.UUID;

public final class TenantDev {
    private TenantDev() {}

    public static UUID NEGOCIO_ID()     { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }
    public static UUID DISPOSITIVO_ID() { return UUID.fromString("00000000-0000-0000-0000-000000000002"); }
    public static UUID CAJERO_ID()      { return UUID.fromString("00000000-0000-0000-0000-000000000003"); }
    public static UUID TURNO_ID()       { return UUID.fromString("00000000-0000-0000-0000-000000000004"); }
}