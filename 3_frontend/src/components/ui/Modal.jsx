import { useEffect, useId, useRef } from 'react'
import Boton from './Boton.jsx'
import estilos from './Modal.module.css'

// Confirmación de acciones importantes (HU-06). <dialog> con showModal() atrapa el foco y bloquea el resto de la
// página; Esc no lo cierra por su cuenta: avisa a onCerrar para que el padre decida (y no mientras se envía).
function Modal({
  abierto,
  titulo,
  onConfirmar,
  onCerrar,
  children,
  textoConfirmar = 'Confirmar',
  variante = 'primario',
  cargando = false,
}) {
  const dialogo = useRef(null)
  const idTitulo = useId()

  useEffect(() => {
    const elemento = dialogo.current
    if (!elemento) return
    if (abierto && !elemento.open) elemento.showModal()
    if (!abierto && elemento.open) elemento.close()
  }, [abierto])

  function alCancelar(evento) {
    evento.preventDefault()
    if (!cargando) onCerrar()
  }

  return (
    <dialog ref={dialogo} className={estilos.modal} aria-labelledby={idTitulo} onCancel={alCancelar}>
      <h2 id={idTitulo} className={estilos.titulo}>
        {titulo}
      </h2>
      <div className={estilos.contenido}>{children}</div>
      <div className={estilos.pie}>
        <Boton variante="secundario" deshabilitado={cargando} onClick={onCerrar}>
          Cancelar
        </Boton>
        <Boton variante={variante} cargando={cargando} onClick={onConfirmar}>
          {textoConfirmar}
        </Boton>
      </div>
    </dialog>
  )
}

export default Modal
