import { PAPEL_PROPIO, PAPELES_RECLAMO, SIN_MENSAJES, TITULO_HILO, fechaHora } from './textosAdministracion.js'
import estilos from './HiloReclamo.module.css'

// HU-26: el hilo de un reclamo, en el orden de la API. Con "papelPropio" (la parte que mira) sus mensajes dicen "Tú";
// el admin no lo pasa y ve "Usuario"/"Arrendador". El texto se muestra como texto, nunca como HTML.
function HiloReclamo({ mensajes, papelPropio = null }) {
  return (
    <section className={estilos.hilo}>
      <h3 className={estilos.titulo}>{TITULO_HILO}</h3>
      {mensajes.length === 0 ? (
        <p className={estilos.secundario}>{SIN_MENSAJES}</p>
      ) : (
        <ol className={estilos.lista}>
          {mensajes.map((mensaje) => (
            <li key={mensaje.id} className={estilos.mensaje}>
              <p className={estilos.autor}>
                {mensaje.papel === papelPropio ? PAPEL_PROPIO : PAPELES_RECLAMO[mensaje.papel]}
                <span className={estilos.fecha}> · {fechaHora(mensaje.fecha)}</span>
              </p>
              <p className={estilos.texto}>{mensaje.texto}</p>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

export default HiloReclamo
