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
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http, ValidadorSesion validador, JsonMapper json)
            throws Exception {
        // El token viaja en el encabezado Authorization: el navegador no lo envía solo, así que CSRF no aplica
        // (HU-03, DT-3). Tampoco hay sesión HTTP: cada solicitud se autentica con su token.
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .exceptionHandling(errores -> errores.authenticationEntryPoint(new PuntoEntradaNoAutenticado(json)))
                .addFilterBefore(new FiltroAutenticacionSesion(validador), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers(HttpMethod.GET, "/api/politicas-privacidad/vigente").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/arrendadores").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sesiones").permitAll()
                        // La URL firmada es la autorización (HU-05, DT-3): el navegador la abre sin token.
                        .requestMatchers(HttpMethod.GET, "/api/archivos").permitAll()
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
