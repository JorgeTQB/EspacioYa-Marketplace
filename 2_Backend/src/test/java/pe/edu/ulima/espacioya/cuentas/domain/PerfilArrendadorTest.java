package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PerfilArrendadorTest {

    @Test
    @DisplayName("HU-02 R2: crear deja el perfil NO_INICIADA, sin motivo de rechazo y con el tipo enviado")
    void crearDejaNoIniciada() {
        var perfil = PerfilArrendador.crear(7L, TipoDocumento.RUC, "20100070970", "a".repeat(64),
                "  Canchas del Sur SAC ", " Av. Siempre Viva 123 ");

        assertThat(perfil.getUsuarioId()).isEqualTo(7L);
        assertThat(perfil.getId()).isEqualTo(7L);
        assertThat(perfil.getTipoDocumento()).isEqualTo(TipoDocumento.RUC);
        assertThat(perfil.getNumeroDocumento()).isEqualTo("20100070970");
        assertThat(perfil.getHashDocumento()).isEqualTo("a".repeat(64));
        assertThat(perfil.getRazonSocial()).isEqualTo("Canchas del Sur SAC");
        assertThat(perfil.getDireccionFiscal()).isEqualTo("Av. Siempre Viva 123");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.NO_INICIADA);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-02 R2: un perfil recién creado es nuevo para que save haga persist")
    void perfilNuevo() {
        var perfil = PerfilArrendador.crear(7L, TipoDocumento.DNI, "12345678", "b".repeat(64), "Ana", "Lima");

        assertThat(perfil.isNew()).isTrue();
        perfil.marcarGuardado();
        assertThat(perfil.isNew()).isFalse();
    }

    private static PerfilArrendador perfilEn(EstadoIdentidad estado, String motivo) {
        var perfil = PerfilArrendador.crear(7L, TipoDocumento.RUC, "20100070970", "a".repeat(64),
                "Canchas SAC", "Lima");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        ReflectionTestUtils.setField(perfil, "motivoRechazo", motivo);
        return perfil;
    }

    @Test
    @DisplayName("HU-05 R24: puede enviar documentos solo en NO_INICIADA o RECHAZADA")
    void puedeEnviarDocumentosPorEstado() {
        assertThat(perfilEn(EstadoIdentidad.NO_INICIADA, null).puedeEnviarDocumentos()).isTrue();
        assertThat(perfilEn(EstadoIdentidad.RECHAZADA, "Ilegible").puedeEnviarDocumentos()).isTrue();
        assertThat(perfilEn(EstadoIdentidad.EN_REVISION, null).puedeEnviarDocumentos()).isFalse();
        assertThat(perfilEn(EstadoIdentidad.VERIFICADA, null).puedeEnviarDocumentos()).isFalse();
    }

    @Test
    @DisplayName("HU-05 R2: enviar documentos desde NO_INICIADA deja el perfil EN_REVISION")
    void enviarDesdeNoIniciada() {
        var perfil = perfilEn(EstadoIdentidad.NO_INICIADA, null);

        perfil.enviarDocumentos();

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-05 R4: enviar documentos desde RECHAZADA deja EN_REVISION y el motivo de rechazo nulo")
    void enviarDesdeRechazadaLimpiaMotivo() {
        var perfil = perfilEn(EstadoIdentidad.RECHAZADA, "El documento está borroso.");

        perfil.enviarDocumentos();

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-05 R16: enviar documentos desde EN_REVISION o VERIFICADA lanza y no cambia el estado")
    void enviarDesdeEstadoNoPermitidoLanza() {
        for (var estado : new EstadoIdentidad[] {EstadoIdentidad.EN_REVISION, EstadoIdentidad.VERIFICADA}) {
            var perfil = perfilEn(estado, null);

            assertThatThrownBy(perfil::enviarDocumentos).isInstanceOf(IllegalStateException.class);
            assertThat(perfil.getEstadoIdentidad()).isEqualTo(estado);
        }
    }

    @Test
    @DisplayName("HU-05 R28: el aviso de EN_REVISION dice que los documentos están en revisión")
    void avisoEnRevision() {
        assertThat(perfilEn(EstadoIdentidad.EN_REVISION, null).avisoIdentidad())
                .isEqualTo("Estamos revisando tus documentos de identidad. Te avisaremos cuando terminemos.");
        assertThat(perfilEn(EstadoIdentidad.NO_INICIADA, null).avisoIdentidad())
                .isEqualTo("Para publicar tus espacios, primero debes verificar tu identidad.");
        assertThat(perfilEn(EstadoIdentidad.VERIFICADA, null).avisoIdentidad()).isNull();
    }

    @Test
    @DisplayName("HU-05 R29: el aviso de RECHAZADA incluye el motivo de rechazo guardado")
    void avisoRechazadaConMotivo() {
        assertThat(perfilEn(EstadoIdentidad.RECHAZADA, "El documento está borroso.").avisoIdentidad())
                .isEqualTo("Tu identidad fue rechazada. Vuelve a subir tus documentos. Motivo: "
                        + "El documento está borroso.");
    }

    @Test
    @DisplayName("HU-06 R28: aprobar desde EN_REVISION deja VERIFICADA, sin motivo y sin aviso")
    void aprobarDesdeEnRevision() {
        var perfil = perfilEn(EstadoIdentidad.EN_REVISION, null);

        perfil.aprobarIdentidad();

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
        assertThat(perfil.getMotivoRechazo()).isNull();
        assertThat(perfil.avisoIdentidad()).isNull();
    }

    @Test
    @DisplayName("HU-06 R32: rechazar desde EN_REVISION deja RECHAZADA con el motivo recortado en el aviso")
    void rechazarDesdeEnRevision() {
        var perfil = perfilEn(EstadoIdentidad.EN_REVISION, null);

        perfil.rechazarIdentidad("  El documento está borroso.  ");

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.RECHAZADA);
        assertThat(perfil.getMotivoRechazo()).isEqualTo("El documento está borroso.");
        assertThat(perfil.avisoIdentidad()).isEqualTo("Tu identidad fue rechazada. Vuelve a subir tus documentos. "
                + "Motivo: El documento está borroso.");
    }

    @Test
    @DisplayName("HU-06 R38: aprobar o rechazar fuera de EN_REVISION lanza y no cambia el estado ni el motivo")
    void decidirFueraDeEnRevisionLanza() {
        for (var estado : new EstadoIdentidad[] {EstadoIdentidad.NO_INICIADA, EstadoIdentidad.VERIFICADA,
                EstadoIdentidad.RECHAZADA}) {
            var motivo = estado == EstadoIdentidad.RECHAZADA ? "Ilegible" : null;
            var perfil = perfilEn(estado, motivo);

            assertThatThrownBy(perfil::aprobarIdentidad).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> perfil.rechazarIdentidad("Otro motivo")).isInstanceOf(IllegalStateException.class);
            assertThat(perfil.getEstadoIdentidad()).isEqualTo(estado);
            assertThat(perfil.getMotivoRechazo()).isEqualTo(motivo);
        }
    }

    @Test
    @DisplayName("HU-06 R32: rechazar con el motivo nulo o en blanco lanza y deja el perfil EN_REVISION")
    void rechazarSinMotivoLanza() {
        for (var motivo : new String[] {null, "", "   "}) {
            var perfil = perfilEn(EstadoIdentidad.EN_REVISION, null);

            assertThatThrownBy(() -> perfil.rechazarIdentidad(motivo)).isInstanceOf(IllegalStateException.class);
            assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
            assertThat(perfil.getMotivoRechazo()).isNull();
        }
    }

    // ── HU-20 ────────────────────────────────────────────────────────────

    private static final String RUC_NUEVO = "20131312955";
    private static final String HASH_NUEVO = "c".repeat(64);

    @Test
    @DisplayName("HU-20 R37: con la identidad VERIFICADA, otro número de documento la deja EN_REVISION")
    void verificadaConOtroDocumentoVuelveARevision() {
        var perfil = perfilEn(EstadoIdentidad.VERIFICADA, null);

        var cambio = perfil.actualizarFacturacion(TipoDocumento.RUC, RUC_NUEVO, HASH_NUEVO, "Canchas SAC", "Lima");

        assertThat(cambio.campos()).containsExactly(CampoFacturacion.NUMERO_DOCUMENTO);
        assertThat(cambio.identidadAnterior()).isEqualTo(EstadoIdentidad.VERIFICADA);
        assertThat(cambio.identidadNueva()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getNumeroDocumento()).isEqualTo(RUC_NUEVO);
        assertThat(perfil.getHashDocumento()).isEqualTo(HASH_NUEVO);
    }

    @Test
    @DisplayName("HU-20 R37: con la identidad VERIFICADA, cambiar el tipo y el número la deja EN_REVISION")
    void verificadaConOtroTipoVuelveARevision() {
        var perfil = perfilEn(EstadoIdentidad.VERIFICADA, null);

        var cambio = perfil.actualizarFacturacion(TipoDocumento.DNI, "44556677", HASH_NUEVO, "Canchas SAC", "Lima");

        assertThat(cambio.campos()).containsExactlyInAnyOrder(CampoFacturacion.TIPO_DOCUMENTO,
                CampoFacturacion.NUMERO_DOCUMENTO);
        assertThat(cambio.nombresDeCampos()).isEqualTo("tipoDocumento,numeroDocumento");
        assertThat(perfil.getTipoDocumento()).isEqualTo(TipoDocumento.DNI);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
    }

    @Test
    @DisplayName("HU-20 R38: con la identidad VERIFICADA, otra razón social la deja EN_REVISION")
    void verificadaConOtraRazonSocialVuelveARevision() {
        var perfil = perfilEn(EstadoIdentidad.VERIFICADA, null);

        var cambio = perfil.actualizarFacturacion(TipoDocumento.RUC, null, null, " Canchas Nuevas SAC ", "Lima");

        assertThat(cambio.campos()).containsExactly(CampoFacturacion.RAZON_SOCIAL);
        assertThat(cambio.cambiaDocumento()).isFalse();
        assertThat(cambio.cambiaIdentidad()).isTrue();
        assertThat(perfil.getRazonSocial()).isEqualTo("Canchas Nuevas SAC");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getNumeroDocumento()).isEqualTo("20100070970");
    }

    @Test
    @DisplayName("HU-20 R39: con la identidad VERIFICADA, cambiar solo la dirección la mantiene VERIFICADA")
    void verificadaSoloDireccionSigueVerificada() {
        var perfil = perfilEn(EstadoIdentidad.VERIFICADA, null);

        var cambio = perfil.actualizarFacturacion(TipoDocumento.RUC, "20100070970", null, "Canchas SAC",
                "Av. Nueva 456");

        assertThat(cambio.campos()).containsExactly(CampoFacturacion.DIRECCION_FISCAL);
        assertThat(cambio.cambiaIdentidad()).isFalse();
        assertThat(cambio.identidadNueva()).isEqualTo(EstadoIdentidad.VERIFICADA);
        assertThat(perfil.getDireccionFiscal()).isEqualTo("Av. Nueva 456");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
        assertThat(perfil.getHashDocumento()).isEqualTo("a".repeat(64));
    }

    @Test
    @DisplayName("HU-20 R40: NO_INICIADA y RECHAZADA cambian el documento sin cambiar su estado ni su motivo")
    void noIniciadaYRechazadaConservanEstado() {
        var noIniciada = perfilEn(EstadoIdentidad.NO_INICIADA, null);
        var rechazada = perfilEn(EstadoIdentidad.RECHAZADA, "Ilegible");

        noIniciada.actualizarFacturacion(TipoDocumento.RUC, RUC_NUEVO, HASH_NUEVO, "Otra SAC", "Lima");
        rechazada.actualizarFacturacion(TipoDocumento.RUC, RUC_NUEVO, HASH_NUEVO, "Otra SAC", "Lima");

        assertThat(noIniciada.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.NO_INICIADA);
        assertThat(rechazada.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.RECHAZADA);
        assertThat(rechazada.getMotivoRechazo()).isEqualTo("Ilegible");
        assertThat(rechazada.getNumeroDocumento()).isEqualTo(RUC_NUEVO);
    }

    @Test
    @DisplayName("HU-20 R41: EN_REVISION no admite otro documento ni razón social, pero sí otra dirección")
    void enRevisionNoAdmiteCambioDeIdentidad() {
        var perfil = perfilEn(EstadoIdentidad.EN_REVISION, null);

        assertThat(perfil.admiteCambioDeIdentidad()).isFalse();
        assertThat(perfilEn(EstadoIdentidad.VERIFICADA, null).admiteCambioDeIdentidad()).isTrue();
        assertThat(perfil.cambiosPropuestos(TipoDocumento.RUC, RUC_NUEVO, "Canchas SAC", "Lima").cambiaIdentidad())
                .isTrue();
        assertThatThrownBy(() -> perfil.actualizarFacturacion(TipoDocumento.RUC, RUC_NUEVO, HASH_NUEVO,
                "Canchas SAC", "Lima")).isInstanceOf(IllegalStateException.class);
        assertThat(perfil.getNumeroDocumento()).isEqualTo("20100070970");

        perfil.actualizarFacturacion(TipoDocumento.RUC, null, null, "Canchas SAC", "Av. Nueva 456");

        assertThat(perfil.getDireccionFiscal()).isEqualTo("Av. Nueva 456");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
    }

    @Test
    @DisplayName("HU-20 R36, R43: sin número conserva el actual; con los mismos datos no hay cambios")
    void sinCambios() {
        var perfil = perfilEn(EstadoIdentidad.VERIFICADA, null);

        var propuesto = perfil.cambiosPropuestos(TipoDocumento.RUC, null, " Canchas SAC ", "Lima");

        assertThat(propuesto.hayCambios()).isFalse();
        assertThat(propuesto.nombresDeCampos()).isEmpty();
        assertThat(perfil.cambiosPropuestos(TipoDocumento.RUC, "20100070970", "Canchas SAC", "Lima").hayCambios())
                .isFalse();
        assertThat(propuesto.identidadNueva()).isEqualTo(EstadoIdentidad.VERIFICADA);
    }

    @Test
    @DisplayName("HU-20 R35: cambiar el tipo sin número nuevo o el número sin huella no se aplica")
    void tipoSinNumeroYNumeroSinHuella() {
        var perfil = perfilEn(EstadoIdentidad.NO_INICIADA, null);

        assertThatThrownBy(() -> perfil.actualizarFacturacion(TipoDocumento.DNI, null, null, "Canchas SAC", "Lima"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> perfil.actualizarFacturacion(TipoDocumento.RUC, RUC_NUEVO, null, "Canchas SAC",
                "Lima")).isInstanceOf(IllegalArgumentException.class);
        assertThat(perfil.getTipoDocumento()).isEqualTo(TipoDocumento.RUC);
        assertThat(perfil.getNumeroDocumento()).isEqualTo("20100070970");
    }

    @Test
    @DisplayName("HU-20 R6: numeroEnmascarado deja solo los 4 últimos dígitos")
    void numeroEnmascarado() {
        assertThat(perfilEn(EstadoIdentidad.VERIFICADA, null).numeroEnmascarado()).isEqualTo("****0970");
    }
}
