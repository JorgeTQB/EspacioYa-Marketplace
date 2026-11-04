import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import {
  bloquearFecha,
  consultarDisponibilidad,
  desbloquearFecha,
  guardarHorario,
  listarBloqueos,
  obtenerHorario,
  obtenerMiEspacio,
} from '../../api/espacios.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import SelectorBloques from '../../components/ui/SelectorBloques.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { DIAS_SEMANA, TEXTO_ESTADO_BLOQUE, formatoHora } from './textosEspacios.js'
import estilos from './DisponibilidadEspacio.module.css'

const NO_ENCONTRADO = 404
const ESTADOS_NO_EDITABLES = ['BAJA', 'OCULTO']
const MENSAJE_NO_EDITABLE = 'Este espacio ya no se puede editar.'
const MENSAJE_HORARIO_GUARDADO =
  'Guardamos el horario. Esos bloques ya se pueden reservar en las próximas 8 semanas.'
const AYUDA_HORARIO = 'Los bloques son de 1 hora. Se pueden reservar en las próximas 8 semanas.'
const AYUDA_BLOQUEO = 'Ese día no se podrá reservar ninguna hora.'
const FRANJA_POR_DEFECTO = { horaInicio: '8', horaFin: '22' }
const OPCIONES_DESDE = Array.from({ length: 24 }, (_, hora) => ({ valor: String(hora), etiqueta: formatoHora(hora) }))
const OPCIONES_HASTA = Array.from({ length: 24 }, (_, i) => {
  const hora = i + 1
  return { valor: String(hora), etiqueta: hora === 24 ? '24:00 (medianoche)' : formatoHora(hora) }
})
const FORMATO_FECHA = new Intl.DateTimeFormat('es-PE', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})
const CAMPO_FRANJA = /^franjas\[(\d+)\](?:\.(\w+))?$/

let siguienteClave = 1

function nuevaFranja(diaSemana, horas) {
  return { clave: siguienteClave++, diaSemana, horaInicio: horas.horaInicio, horaFin: horas.horaFin }
}

function franjasDe(horario) {
  return horario.franjas.map((franja) =>
    nuevaFranja(franja.diaSemana, { horaInicio: String(franja.horaInicio), horaFin: String(franja.horaFin) }),
  )
}

function numeroONulo(texto) {
  return texto === '' ? null : Number(texto)
}

function fechaEnTexto(fecha) {
  return FORMATO_FECHA.format(new Date(`${fecha}T00:00:00Z`))
}

// Ubica cada error de la API bajo su franja: franjas[i] y franjas[i].horaFin van bajo "Hasta".
function erroresDeHorario(errores, clavesEnviadas, franjas) {
  const porFranja = {}
  const porDia = {}
  const generales = []
  for (const { campo, mensaje } of errores) {
    const coincidencia = CAMPO_FRANJA.exec(campo)
    if (!coincidencia) {
      generales.push(mensaje)
      continue
    }
    const clave = clavesEnviadas[Number(coincidencia[1])]
    const subcampo = coincidencia[2] ?? 'horaFin'
    if (subcampo === 'diaSemana') {
      const franja = franjas.find((f) => f.clave === clave)
      if (franja) porDia[franja.diaSemana] = mensaje
      continue
    }
    porFranja[clave] = { ...porFranja[clave], [subcampo]: mensaje }
  }
  return { porFranja, porDia, generales }
}

const SIN_ERRORES_HORARIO = { porFranja: {}, porDia: {}, generales: [] }

function DisponibilidadEspacio() {
  const { id } = useParams()
  const { sesion } = useSesion()
  const esArrendador = sesion?.rol === 'ARRENDADOR'

  const [espacio, setEspacio] = useState(null)
  const [noEncontrado, setNoEncontrado] = useState(null)
  const [errorCarga, setErrorCarga] = useState(null)

  const [franjas, setFranjas] = useState([])
  const [erroresHorario, setErroresHorario] = useState(SIN_ERRORES_HORARIO)
  const [alertaHorario, setAlertaHorario] = useState(null)
  const [guardando, setGuardando] = useState(false)

  const [bloqueos, setBloqueos] = useState(null)
  const [fechaBloqueo, setFechaBloqueo] = useState('')
  const [errorFecha, setErrorFecha] = useState(null)
  const [alertaBloqueos, setAlertaBloqueos] = useState(null)
  const [bloqueando, setBloqueando] = useState(false)
  const [desbloqueando, setDesbloqueando] = useState(null)

  const [fechaDia, setFechaDia] = useState('')
  const [dia, setDia] = useState(null)
  const [errorDia, setErrorDia] = useState(null)

  useEffect(() => {
    if (!esArrendador) return undefined
    let activo = true
    Promise.all([obtenerMiEspacio(id), obtenerHorario(id), listarBloqueos(id)])
      .then(([datosEspacio, horario, datosBloqueos]) => {
        if (!activo) return
        setEspacio(datosEspacio)
        setFranjas(franjasDe(horario))
        setBloqueos(datosBloqueos)
      })
      .catch((error) => {
        if (!activo) return
        if (error.estado === NO_ENCONTRADO) {
          setNoEncontrado(error.mensaje)
        } else {
          setErrorCarga(error.mensaje)
        }
      })
    return () => {
      activo = false
    }
  }, [esArrendador, id])

  if (!esArrendador) return <Navigate to="/panel" replace />

  if (noEncontrado) {
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="error">
          {noEncontrado} <Link to="/panel/espacios">Volver a mis espacios</Link>
        </Alerta>
      </section>
    )
  }

  if (errorCarga) {
    return (
      <section className={estilos.pantalla}>
        <Alerta tipo="error">{errorCarga}</Alerta>
      </section>
    )
  }

  if (!espacio || String(espacio.id) !== id || !bloqueos) {
    return (
      <section className={estilos.pantalla}>
        <p className={estilos.secundario}>Cargando…</p>
      </section>
    )
  }

  const noEditable = ESTADOS_NO_EDITABLES.includes(espacio.estado)

  async function refrescarDia(fecha = fechaDia) {
    if (!fecha) {
      setDia(null)
      return
    }
    setErrorDia(null)
    try {
      setDia(await consultarDisponibilidad(id, fecha))
    } catch (error) {
      setDia(null)
      setErrorDia({
        texto: error.mensaje,
        campo: error.errores.find((e) => e.campo === 'fecha')?.mensaje ?? null,
      })
    }
  }

  function cambiarFecha(fecha) {
    setFechaDia(fecha)
    refrescarDia(fecha)
  }

  function agregarFranja(diaSemana) {
    setFranjas((previas) => [...previas, nuevaFranja(diaSemana, FRANJA_POR_DEFECTO)])
  }

  function quitarFranja(clave) {
    setFranjas((previas) => previas.filter((franja) => franja.clave !== clave))
  }

  function cambiarFranja(clave, campo) {
    return (valor) =>
      setFranjas((previas) => previas.map((franja) => (franja.clave === clave ? { ...franja, [campo]: valor } : franja)))
  }

  async function alGuardarHorario() {
    const ordenadas = DIAS_SEMANA.flatMap((d) => franjas.filter((franja) => franja.diaSemana === d.valor))
    const clavesEnviadas = ordenadas.map((franja) => franja.clave)
    const cuerpo = ordenadas.map((franja) => ({
      diaSemana: franja.diaSemana,
      horaInicio: numeroONulo(franja.horaInicio),
      horaFin: numeroONulo(franja.horaFin),
    }))
    setGuardando(true)
    setAlertaHorario(null)
    setErroresHorario(SIN_ERRORES_HORARIO)
    try {
      const horario = await guardarHorario(id, cuerpo)
      setFranjas(franjasDe(horario))
      setAlertaHorario({ tipo: 'exito', texto: MENSAJE_HORARIO_GUARDADO })
      refrescarDia()
    } catch (error) {
      const ubicados = erroresDeHorario(error.errores, clavesEnviadas, ordenadas)
      setErroresHorario(ubicados)
      setAlertaHorario({ tipo: 'error', texto: [error.mensaje, ...ubicados.generales].join(' ') })
    } finally {
      setGuardando(false)
    }
  }

  async function recargarBloqueos() {
    setBloqueos(await listarBloqueos(id))
  }

  async function alBloquear() {
    setBloqueando(true)
    setAlertaBloqueos(null)
    setErrorFecha(null)
    try {
      const creado = await bloquearFecha(id, fechaBloqueo)
      setAlertaBloqueos({
        tipo: 'exito',
        texto: `Bloqueamos el ${fechaEnTexto(creado.fecha)}. Sus bloques ya no están disponibles.`,
      })
      setFechaBloqueo('')
      await recargarBloqueos()
      refrescarDia()
    } catch (error) {
      setErrorFecha(error.errores.find((e) => e.campo === 'fecha')?.mensaje ?? null)
      setAlertaBloqueos({ tipo: 'error', texto: error.mensaje })
    } finally {
      setBloqueando(false)
    }
  }

  async function alDesbloquear(fecha) {
    setDesbloqueando(fecha)
    setAlertaBloqueos(null)
    try {
      await desbloquearFecha(id, fecha)
      setAlertaBloqueos({ tipo: 'exito', texto: `Desbloqueamos el ${fechaEnTexto(fecha)}.` })
      await recargarBloqueos()
      refrescarDia()
    } catch (error) {
      setAlertaBloqueos({ tipo: 'error', texto: error.mensaje })
    } finally {
      setDesbloqueando(null)
    }
  }

  const bloquesVista = (dia?.bloques ?? []).map((bloque) => ({
    horaInicio: bloque.horaInicio,
    horaFin: bloque.horaFin,
    libre: bloque.estado === 'LIBRE',
    detalle: TEXTO_ESTADO_BLOQUE[bloque.estado],
  }))

  return (
    <section className={estilos.pantalla}>
      <div className={estilos.encabezado}>
        <h2>Disponibilidad de {espacio.nombre}</h2>
        <EtiquetaEstado estado={espacio.estado} />
      </div>
      <p>
        <Link to="/panel/espacios">Volver a mis espacios</Link>
      </p>
      {noEditable && <Alerta tipo="info">{MENSAJE_NO_EDITABLE}</Alerta>}

      <div className={estilos.secciones}>
        <Tarjeta titulo="Horario semanal">
          {alertaHorario && <Alerta tipo={alertaHorario.tipo}>{alertaHorario.texto}</Alerta>}
          <p className={estilos.secundario}>{AYUDA_HORARIO}</p>
          <ul className={estilos.dias}>
            {DIAS_SEMANA.map((d) => {
              const delDia = franjas.filter((franja) => franja.diaSemana === d.valor)
              return (
                <li key={d.valor} className={estilos.dia}>
                  <h4 className={estilos.nombreDia}>{d.etiqueta}</h4>
                  {erroresHorario.porDia[d.valor] && (
                    <p className={estilos.error}>{erroresHorario.porDia[d.valor]}</p>
                  )}
                  {delDia.length === 0 && <p className={estilos.secundario}>Cerrado</p>}
                  {delDia.map((franja) => {
                    const errores = erroresHorario.porFranja[franja.clave] ?? {}
                    return (
                      <div key={franja.clave} className={estilos.franja}>
                        <Campo
                          etiqueta="Desde"
                          nombre={`desde-${franja.clave}`}
                          tipo="select"
                          valor={franja.horaInicio}
                          onChange={cambiarFranja(franja.clave, 'horaInicio')}
                          opciones={OPCIONES_DESDE}
                          error={errores.horaInicio}
                        />
                        <Campo
                          etiqueta="Hasta"
                          nombre={`hasta-${franja.clave}`}
                          tipo="select"
                          valor={franja.horaFin}
                          onChange={cambiarFranja(franja.clave, 'horaFin')}
                          opciones={OPCIONES_HASTA}
                          error={errores.horaFin}
                        />
                        <div className={estilos.quitar}>
                          <Boton
                            variante="secundario"
                            deshabilitado={noEditable}
                            onClick={() => quitarFranja(franja.clave)}
                          >
                            Quitar
                          </Boton>
                        </div>
                      </div>
                    )
                  })}
                  <div>
                    <Boton variante="secundario" deshabilitado={noEditable} onClick={() => agregarFranja(d.valor)}>
                      Agregar franja
                    </Boton>
                  </div>
                </li>
              )
            })}
          </ul>
          <div>
            <Boton cargando={guardando} deshabilitado={noEditable} onClick={alGuardarHorario}>
              Guardar horario
            </Boton>
          </div>
        </Tarjeta>

        <Tarjeta titulo="Fechas bloqueadas">
          {alertaBloqueos && <Alerta tipo={alertaBloqueos.tipo}>{alertaBloqueos.texto}</Alerta>}
          <div className={estilos.formularioBloqueo}>
            <Campo
              etiqueta="Fecha a bloquear"
              nombre="fecha-bloqueo"
              tipo="date"
              valor={fechaBloqueo}
              onChange={setFechaBloqueo}
              min={bloqueos.desde}
              max={bloqueos.hasta}
              ayuda={AYUDA_BLOQUEO}
              error={errorFecha}
            />
            <div>
              <Boton variante="secundario" cargando={bloqueando} deshabilitado={noEditable} onClick={alBloquear}>
                Bloquear fecha
              </Boton>
            </div>
          </div>
          {bloqueos.fechas.length === 0 ? (
            <p className={estilos.secundario}>No tienes fechas bloqueadas.</p>
          ) : (
            <ul className={estilos.fechas}>
              {bloqueos.fechas.map((fecha) => (
                <li key={fecha} className={estilos.fecha}>
                  <span>{fechaEnTexto(fecha)}</span>
                  <Boton
                    variante="secundario"
                    cargando={desbloqueando === fecha}
                    deshabilitado={noEditable}
                    onClick={() => alDesbloquear(fecha)}
                  >
                    Desbloquear
                  </Boton>
                </li>
              ))}
            </ul>
          )}
        </Tarjeta>

        <Tarjeta titulo="Ver un día">
          {errorDia && <Alerta tipo="error">{errorDia.texto}</Alerta>}
          <Campo
            etiqueta="Fecha"
            nombre="fecha-dia"
            tipo="date"
            valor={fechaDia}
            onChange={cambiarFecha}
            min={bloqueos.desde}
            max={bloqueos.hasta}
            error={errorDia?.campo}
          />
          {dia?.bloqueada && <p className={estilos.secundario}>Esta fecha está bloqueada.</p>}
          {dia && bloquesVista.length === 0 && (
            <p className={estilos.secundario}>Ese día el espacio está cerrado según tu horario.</p>
          )}
          <SelectorBloques bloques={bloquesVista} />
        </Tarjeta>
      </div>
    </section>
  )
}

export default DisponibilidadEspacio
