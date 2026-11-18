package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;

/** ★ ConcreteDecorator transaccional, con un doble de su envuelta y un gestor de transacciones simulado (R47, R48). */
@ExtendWith(MockitoExtension.class)
class PublicacionTransaccionalTest {

    private static final SolicitudResena SOLICITUD = new SolicitudResena(7L, 90L, 4, "Buena", "10.0.0.1");

    @Mock
    private PublicacionResena envuelta;
    @Mock
    private PlatformTransactionManager gestor;
    @Mock
    private TransactionStatus estado;

    private PublicacionTransaccional transaccional;

    @BeforeEach
    void preparar() {
        transaccional = new PublicacionTransaccional(envuelta, new TransactionTemplate(gestor));
        when(gestor.getTransaction(any())).thenReturn(estado);
    }

    @Test
    @DisplayName("HU-24 R47: si la envuelta vuelve, invoca dentro de la transacción y la confirma")
    void confirmaSiVuelve() {
        var devuelta = Resena.nueva(90L, 7L, 40L, 4, "Buena", Instant.parse("2026-10-28T15:00:00Z"));
        when(envuelta.publicar(SOLICITUD)).thenReturn(devuelta);

        assertThat(transaccional.publicar(SOLICITUD)).isSameAs(devuelta);

        verify(gestor).commit(estado);
        verify(gestor, never()).rollback(any());
    }

    @Test
    @DisplayName("HU-24 R47: si la envuelta lanza, revierte la transacción y no la confirma")
    void revierteSiLanza() {
        var error = new RecursoNoEncontradoException();
        when(envuelta.publicar(SOLICITUD)).thenThrow(error);

        assertThatThrownBy(() -> transaccional.publicar(SOLICITUD)).isSameAs(error);

        verify(gestor).rollback(estado);
        verify(gestor, never()).commit(any());
    }

    @Test
    @DisplayName("HU-24 R48: la violación de uq_resena_reserva se traduce a ya reseñada")
    void traduceLaCarrera() {
        when(envuelta.publicar(SOLICITUD)).thenThrow(new DataIntegrityViolationException("x",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uq_resena_reserva\"")));

        assertThatThrownBy(() -> transaccional.publicar(SOLICITUD)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_YA_RESENADA);

        verify(gestor).rollback(estado);
    }

    @Test
    @DisplayName("HU-24 R48: otra violación de integridad se relanza igual")
    void otraViolacionIgual() {
        var otra = new DataIntegrityViolationException("x", new SQLException("viola fk_resena_espacio"));
        when(envuelta.publicar(SOLICITUD)).thenThrow(otra);

        assertThatThrownBy(() -> transaccional.publicar(SOLICITUD)).isSameAs(otra);
    }

    @Test
    @DisplayName("HU-24 R48: una excepción que no es de integridad se relanza igual")
    void otraExcepcionIgual() {
        var otra = new ReglaNegocioException(ResenaService.MENSAJE_PLAZO_VENCIDO);
        when(envuelta.publicar(SOLICITUD)).thenThrow(otra);

        assertThatThrownBy(() -> transaccional.publicar(SOLICITUD)).isSameAs(otra);
    }
}
