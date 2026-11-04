package pe.edu.ulima.espacioya.pagos.dto;

import java.util.List;

/** Una página de liquidaciones de 20 en 20, sin total, como HU-10 y HU-14 (HU-19 S9). */
public record PaginaLiquidacionesDTO<T>(int pagina, List<T> liquidaciones, boolean hayMas) {
}
