package pe.edu.ulima.espacioya.cuentas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PoliticaPrivacidad {

    private final String versionVigente;

    public PoliticaPrivacidad(@Value("${espacioya.privacidad.version-vigente}") String versionVigente) {
        this.versionVigente = versionVigente;
    }

    public String versionVigente() {
        return versionVigente;
    }

    public boolean esVigente(String version) {
        return versionVigente.equals(version);
    }
}
