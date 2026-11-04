package pe.edu.ulima.espacioya.comun.seguridad;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ConfiguracionSeguridad {

    private static final int COSTO_BCRYPT = 12;

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, ValidadorSesion validador, JsonMapper json,
                                               IpsPermitidasAdmin ipsAdmin) throws Exception {
        // El token viaja en el encabezado Authorization: el navegador no lo envía solo, así que CSRF no aplica
        // (HU-03, DT-3). Tampoco hay sesión HTTP: cada solicitud se autentica con su token.
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .exceptionHandling(errores -> errores.authenticationEntryPoint(new PuntoEntradaNoAutenticado(json)))
                .addFilterBefore(new FiltroAutenticacionSesion(validador), UsernamePasswordAuthenticationFilter.class)
                // Antes de autenticar (HU-06, DT-1): desde una IP no permitida el panel admin ni siquiera
                // revela si la sesión es válida.
                .addFilterBefore(new FiltroIpsAdmin(ipsAdmin, json), FiltroAutenticacionSesion.class)
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers(HttpMethod.GET, "/api/politicas-privacidad/vigente").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/arrendadores").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sesiones").permitAll()
                        // Segundo paso del inicio de sesión (HU-17 DT-1): la autorización es el desafío del cuerpo,
                        // que no es una sesión. /api/mfa/** sigue exigiendo sesión.
                        .requestMatchers(HttpMethod.POST, "/api/sesiones/mfa").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sesiones/mfa/configuracion").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recuperaciones").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recuperaciones/validaciones").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recuperaciones/confirmaciones").permitAll()
                        // Confirmación del correo nuevo (HU-20 D21): la autorización es el token del cuerpo. Solo POST.
                        .requestMatchers(HttpMethod.POST, "/api/cambios-correo/confirmaciones").permitAll()
                        // La URL firmada es la autorización (HU-05, DT-3): el navegador la abre sin token.
                        .requestMatchers(HttpMethod.GET, "/api/archivos").permitAll()
                        // Fotos de espacios (HU-07, DT-4): públicas si el espacio está publicado; si no, el servicio
                        // exige ser el dueño. El filtro de sesión igual autentica si llega un Bearer válido.
                        .requestMatchers(HttpMethod.GET, "/api/fotos/*").permitAll()
                        // Catálogo público (HU-10 DT-1, DT-2): búsqueda y zonas sin sesión; un Bearer inválido se
                        // trata como anónimo.
                        .requestMatchers(HttpMethod.GET, "/api/espacios").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/zonas").permitAll()
                        // Ficha pública y sus bloques libres (HU-11 DT-12); cualquier otra subruta exige sesión.
                        .requestMatchers(HttpMethod.GET, "/api/espacios/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/espacios/*/disponibilidad").permitAll()
                        // Lo llama la pasarela, sin sesión (HU-13 R22): la autorización es su firma.
                        .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    // El nombre importa: .cors(Customizer.withDefaults()) busca el bean "corsConfigurationSource";
    // con otro nombre Spring Security usa la configuración CORS de Spring MVC (vacía) y no aplica esta.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${espacioya.frontend.url}") String urlFrontend) {
        var configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(urlFrontend));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        var origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/api/**", configuracion);
        return origen;
    }

    @Bean
    public PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder(COSTO_BCRYPT);
    }
}
