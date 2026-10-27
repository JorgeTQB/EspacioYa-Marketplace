package pe.edu.ulima.espacioya.comun.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorDTO> reglaNegocio(ReglaNegocioException ex) {
        if (ex.getCampo() == null) {
            return ResponseEntity.badRequest().body(ErrorDTO.de(ex.getMessage()));
        }
        var error = new ErrorCampoDTO(ex.getCampo(), ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorDTO(ErrorDTO.MENSAJE_VALIDACION, List.of(error)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> validacion(MethodArgumentNotValidException ex) {
        List<ErrorCampoDTO> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorCampoDTO(error.getField(), error.getDefaultMessage()))
                .distinct()
                .toList();
        return ResponseEntity.badRequest().body(new ErrorDTO(ErrorDTO.MENSAJE_VALIDACION, errores));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDTO> cuerpoIlegible(HttpMessageNotReadableException ex) {
        log.debug("Cuerpo de la solicitud ilegible", ex);
        return ResponseEntity.badRequest().body(ErrorDTO.de(ErrorDTO.MENSAJE_VALIDACION));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> inesperado(Exception ex) {
        // Las excepciones propias de Spring MVC (404, 405, 415…) conservan su código, con mensaje genérico.
        if (ex instanceof ErrorResponse respuesta && !respuesta.getStatusCode().is5xxServerError()) {
            log.debug("Solicitud rechazada por Spring MVC", ex);
            return generico(respuesta.getStatusCode());
        }
        log.error("Error no previsto al procesar la solicitud", ex);
        return generico(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ErrorDTO> generico(HttpStatusCode codigo) {
        return ResponseEntity.status(codigo).body(ErrorDTO.de(ErrorDTO.MENSAJE_INESPERADO));
    }
}
