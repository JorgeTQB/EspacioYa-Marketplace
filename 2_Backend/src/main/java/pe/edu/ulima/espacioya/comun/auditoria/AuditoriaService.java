package pe.edu.ulima.espacioya.comun.auditoria;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {

    private final AuditoriaRepository auditorias;
    private final Clock reloj;

    public AuditoriaService(AuditoriaRepository auditorias, Clock reloj) {
        this.auditorias = auditorias;
        this.reloj = reloj;
    }

    @Transactional
    public void registrar(EventoAuditoria evento) {
        auditorias.save(Auditoria.de(evento, Instant.now(reloj)));
    }
}
