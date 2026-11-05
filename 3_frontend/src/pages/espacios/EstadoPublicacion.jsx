import { useState } from 'react'
import { Link } from 'react-router-dom'
import { darDeBajaEspacio, pausarEspacio, reanudarEspacio } from '../../api/espacios.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Modal from '../../components/ui/Modal.jsx'
import useSesion from '../../sesion/useSesion.js'
import ReservasAfectadas from './ReservasAfectadas.jsx'
import {
  BOTON_DAR_DE_BAJA,
  BOTON_PAUSAR,
  BOTON_REANUDAR,
  CONFIRMAR_DAR_DE_BAJA,
  CONFIRMAR_PAUSAR,
  MENSAJE_DADO_DE_BAJA,
  MENSAJE_PAUSADO,
  MENSAJE_REANUDADO,
  TEXTO_DAR_DE_BAJA,
  TEXTO_PAUSAR,
  TITULO_DAR_DE_BAJA,
  TITULO_ESTADO_PUBLICACION,
  TITULO_PAUSAR,
} from './textosEspacios.js'
import estilos from './EstadoPublicacion.module.css'

const CAMPO_RESERVAS = 'reservas'

// La baja se rechaza con 400 en el campo "reservas" si el espacio tiene reservas por terminar (HU-21 DT-8).
function errorDeReservas(falla) {
  return falla.errores?.find((error) => error.campo === CAMPO_RESERVAS)
}

// HU-21: pausar, reanudar y dar de baja un espacio PUBLICADO o PAUSADO. No decide nada: el servidor comprueba el
// estado, la identidad y las reservas; aquí solo se muestran sus respuestas. El "primario" de la pantalla ya es
// "Guardar cambios", así que aquí todos los botones son secundario o peligro.
function EstadoPublicacion({ espacio, onCambio }) {
  const { sesion } = useSesion()
  const [modal, setModal] = useState(null)
  const [enviando, setEnviando] = useState(null)
  const [error, setError] = useState(null)
  const [reservasPorTerminar, setReservasPorTerminar] = useState(null)
  // Cada rechazo por reservas vuelve a montar la lista, para que muestre lo que hay ahora.
  const [intento, setIntento] = useState(0)

  async function ejecutar(accion, llamada, mensaje) {
    setError(null)
    setEnviando(accion)
    try {
      const nuevo = await llamada(espacio.id)
      setModal(null)
      setReservasPorTerminar(null)
      onCambio(nuevo, mensaje)
    } catch (falla) {
      setModal(null)
      const reservas = accion === 'baja' ? errorDeReservas(falla) : null
      if (reservas) {
        setReservasPorTerminar(reservas.mensaje)
        setIntento((anterior) => anterior + 1)
      } else {
        setError({ texto: falla.mensaje, enlaceIdentidad: accion === 'reanudar' && Boolean(sesion?.aviso) })
      }
    } finally {
      setEnviando(null)
    }
  }

  function cerrar() {
    if (!enviando) setModal(null)
  }

  return (
    <div className={estilos.bloque}>
      <h3 className={estilos.titulo}>{TITULO_ESTADO_PUBLICACION}</h3>
      {error && (
        <Alerta tipo="error">
          {error.texto}
          {error.enlaceIdentidad && (
            <>
              {' '}
              <Link to="/panel/identidad">Verificar mi identidad</Link>
            </>
          )}
        </Alerta>
      )}
      <div className={estilos.acciones}>
        {espacio.estado === 'PUBLICADO' && (
          <Boton variante="secundario" deshabilitado={enviando !== null} onClick={() => setModal('pausa')}>
            {BOTON_PAUSAR}
          </Boton>
        )}
        {espacio.estado === 'PAUSADO' && (
          <Boton
            variante="secundario"
            cargando={enviando === 'reanudar'}
            deshabilitado={enviando !== null}
            onClick={() => ejecutar('reanudar', reanudarEspacio, MENSAJE_REANUDADO)}
          >
            {BOTON_REANUDAR}
          </Boton>
        )}
        <Boton variante="peligro" deshabilitado={enviando !== null} onClick={() => setModal('baja')}>
          {BOTON_DAR_DE_BAJA}
        </Boton>
      </div>

      {reservasPorTerminar && (
        <>
          <Alerta tipo="atencion">{reservasPorTerminar}</Alerta>
          <ReservasAfectadas key={intento} espacioId={espacio.id} />
        </>
      )}

      <Modal
        abierto={modal === 'pausa'}
        titulo={TITULO_PAUSAR}
        textoConfirmar={CONFIRMAR_PAUSAR}
        cargando={enviando === 'pausa'}
        onCerrar={cerrar}
        onConfirmar={() => ejecutar('pausa', pausarEspacio, MENSAJE_PAUSADO)}
      >
        <p>{TEXTO_PAUSAR}</p>
      </Modal>
      <Modal
        abierto={modal === 'baja'}
        titulo={TITULO_DAR_DE_BAJA}
        textoConfirmar={CONFIRMAR_DAR_DE_BAJA}
        variante="peligro-confirmar"
        cargando={enviando === 'baja'}
        onCerrar={cerrar}
        onConfirmar={() => ejecutar('baja', darDeBajaEspacio, MENSAJE_DADO_DE_BAJA)}
      >
        <p>{TEXTO_DAR_DE_BAJA}</p>
      </Modal>
    </div>
  )
}

export default EstadoPublicacion
