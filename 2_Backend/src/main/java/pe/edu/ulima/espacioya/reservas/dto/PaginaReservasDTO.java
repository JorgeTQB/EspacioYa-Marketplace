package pe.edu.ulima.espacioya.reservas.dto;

import java.util.List;

/** Una página de un grupo de reservas (HU-14): sin total, como HU-10 DT-8. */
public record PaginaReservasDTO<T>(int pagina, List<T> reservas, boolean hayMas) {

    public PaginaReservasDTO {
        reservas = List.copyOf(reservas);
    }
}
