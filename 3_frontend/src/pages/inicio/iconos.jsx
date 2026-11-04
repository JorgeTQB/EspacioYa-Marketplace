// Iconos de trazo para la página de inicio. Decorativos: siempre aria-hidden, el texto vecino da el significado.
function Icono({ className, children }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  )
}

export function IconoUbicacion(props) {
  return (
    <Icono {...props}>
      <path d="M12 21s-7-6.1-7-11.5A7 7 0 0 1 19 9.5C19 14.9 12 21 12 21Z" />
      <circle cx="12" cy="9.5" r="2.5" />
    </Icono>
  )
}

export function IconoCalendario(props) {
  return (
    <Icono {...props}>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2" />
      <path d="M3.5 10h17M8 3v4M16 3v4M8 14h2M14 14h2M8 17h2" />
    </Icono>
  )
}

export function IconoTipo(props) {
  return (
    <Icono {...props}>
      <path d="M5 8h14l-1 12.5H6L5 8Z" />
      <path d="M9 8V6.5a3 3 0 0 1 6 0V8M9.5 14l2 2 3.5-3.5" />
    </Icono>
  )
}

export function IconoLupa(props) {
  return (
    <Icono {...props}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20 20-4.4-4.4" />
    </Icono>
  )
}

export function IconoFlecha(props) {
  return (
    <Icono {...props}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </Icono>
  )
}

export function IconoCancha(props) {
  return (
    <Icono {...props}>
      <rect x="2.5" y="5" width="19" height="14" rx="1.5" />
      <path d="M12 5v14" />
      <circle cx="12" cy="12" r="2.5" />
      <path d="M2.5 9.5h2.5v5H2.5M21.5 9.5H19v5h2.5" />
    </Icono>
  )
}

export function IconoEstadio(props) {
  return (
    <Icono {...props}>
      <ellipse cx="12" cy="7.5" rx="9" ry="3" />
      <path d="M3 7.5v8c0 1.7 4 3 9 3s9-1.3 9-3v-8" />
      <path d="M7 10.2v8M12 10.5v8M17 10.2v8" />
    </Icono>
  )
}

export function IconoAuditorio(props) {
  return (
    <Icono {...props}>
      <circle cx="12" cy="7.5" r="2.8" />
      <circle cx="5.5" cy="9.5" r="2.2" />
      <circle cx="18.5" cy="9.5" r="2.2" />
      <path d="M7 19.5v-1a5 5 0 0 1 10 0v1M2 18.5v-.5a3.5 3.5 0 0 1 5-3.1M22 18.5v-.5a3.5 3.5 0 0 0-5-3.1" />
    </Icono>
  )
}

export function IconoReserva(props) {
  return (
    <Icono {...props}>
      <rect x="3.5" y="5" width="17" height="15.5" rx="2" />
      <path d="M3.5 10h17M8 3v4M16 3v4M12 12.5v5M9.5 15h5" />
    </Icono>
  )
}

export function IconoEscudo(props) {
  return (
    <Icono {...props}>
      <path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.2 7.5 9.5 4.3-1.3 7.5-4.9 7.5-9.5V6L12 3Z" />
      <path d="m8.8 12 2.2 2.2 4.2-4.4" />
    </Icono>
  )
}

export function IconoVerificado(props) {
  return (
    <Icono {...props}>
      <path d="M12 2.8 14.3 4.5 17.1 4.3 18 7l2.4 1.6-.9 2.7.9 2.7L18 15.6l-.9 2.7-2.8-.2L12 19.8l-2.3-1.7-2.8.2L6 15.6 3.6 14l.9-2.7-.9-2.7L6 7l.9-2.7 2.8.2L12 2.8Z" />
      <path d="m8.8 11.3 2.2 2.2 4.2-4.4" />
    </Icono>
  )
}

export function IconoSoporte(props) {
  return (
    <Icono {...props}>
      <path d="M4 14v-2a8 8 0 0 1 16 0v2" />
      <rect x="3" y="13" width="4" height="6" rx="1.5" />
      <rect x="17" y="13" width="4" height="6" rx="1.5" />
      <path d="M19 19c0 1.4-1.6 2-4 2h-2" />
    </Icono>
  )
}
