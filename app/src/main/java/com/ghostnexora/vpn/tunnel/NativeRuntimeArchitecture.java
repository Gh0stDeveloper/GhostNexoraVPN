package com.ghostnexora.vpn.tunnel;

import com.ghostnexora.vpn.data.model.ConnectionMode;

import java.util.ArrayList;
import java.util.List;

/** Authoritative description of the executable VPN data plane bundled by the app. */
public final class NativeRuntimeArchitecture {
    private static final String XRAY_TUN = "AndroidLibXrayLite · Xray TUN nativo";

    private NativeRuntimeArchitecture() {
    }

    public static NativeRuntimePlan plan(ConnectionMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode == null");
        if (mode.isSsh()) {
            return new NativeRuntimePlan(XRAY_TUN, "JSch SSH", "127.0.0.1 SOCKS5 → SSH direct-tcpip", true, false,
                    "TCP por SSH; BadVPN UDP Gateway no está empaquetado");
        }
        if (mode == ConnectionMode.V2RAY) {
            return new NativeRuntimePlan(XRAY_TUN, "Xray VLESS/VMess", null, true, true,
                    "La compatibilidad exacta depende del transporte admitido por el core incluido");
        }
        if (mode == ConnectionMode.TROJAN) {
            return new NativeRuntimePlan(XRAY_TUN, "Xray Trojan/TLS", null, true, true,
                    "TLS y credenciales se validan con la configuración del perfil");
        }
        if (mode == ConnectionMode.UDP) {
            return new NativeRuntimePlan(XRAY_TUN, "Xray Hysteria2/QUIC", null, true, true,
                    "Hysteria2 habilitado; Hysteria v1 y binario externo no están empaquetados");
        }
        throw new IllegalArgumentException("No existe un runtime nativo para " + mode.getLabel());
    }

    public static String statusLine(ConnectionMode mode) {
        NativeRuntimePlan plan = plan(mode);
        List<String> traffic = new ArrayList<>(2);
        if (plan.isCarriesTcp()) traffic.add("TCP");
        if (plan.isCarriesUdp()) traffic.add("UDP");
        String hop = plan.getLocalHop() != null ? " · " + plan.getLocalHop() : "";
        return plan.getTunAdapter() + " → " + plan.getProtocolCore() + hop + " · " + String.join("/", traffic);
    }
}

final class NativeRuntimePlan {
    private final String tunAdapter;
    private final String protocolCore;
    private final String localHop;
    private final boolean carriesTcp;
    private final boolean carriesUdp;
    private final String limitations;

    NativeRuntimePlan(String tunAdapter, String protocolCore, String localHop, boolean carriesTcp, boolean carriesUdp, String limitations) {
        this.tunAdapter = tunAdapter;
        this.protocolCore = protocolCore;
        this.localHop = localHop;
        this.carriesTcp = carriesTcp;
        this.carriesUdp = carriesUdp;
        this.limitations = limitations;
    }

    public String getTunAdapter() { return tunAdapter; }
    public String getProtocolCore() { return protocolCore; }
    public String getLocalHop() { return localHop; }
    public boolean isCarriesTcp() { return carriesTcp; }
    public boolean isCarriesUdp() { return carriesUdp; }
    public String getLimitations() { return limitations; }
}
