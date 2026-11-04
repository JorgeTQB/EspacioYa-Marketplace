import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { guardarCuentaBancaria, obtenerCuentaBancaria } from '../../api/pagos.js'
import Alerta from '../../components/ui/Alerta.jsx'
import Boton from '../../components/ui/Boton.jsx'
import Campo from '../../components/ui/Campo.jsx'
import EtiquetaEstado from '../../components/ui/EtiquetaEstado.jsx'
import Tarjeta from '../../components/ui/Tarjeta.jsx'
import useSesion from '../../sesion/useSesion.js'
import { fechaHora } from '../administracion/textosAdministracion.js'
import { erroresPorCampo } from '../cuentas/erroresCampo.js'
import {
  AVISO_CAMBIO_CUENTA,
  AVISO_IDENTIDAD_CUENTA,
  AYUDA_CONTRASENA_CUENTA,
  AYUDA_NUMERO_CUENTA,
  BOTON_GUARDAR_CUENTA,
  CARGANDO_CUENTA,
  CUENTA_GUARDADA,
  DESCRIPCION_CUENTA_BANCARIA,
  ENLACE_VERIFICAR_IDENTIDAD,
  ETIQUETA_BANCO,
  ETIQUETA_CONTRASENA_CUENTA,
  ETIQUETA_DECLARACION,
  ETIQUETA_NUMERO_CUENTA,
  SIN_CUENTA_BANCARIA,
  TITULO_CUENTA_BANCARIA,
  TITULO_FORMULARIO_CUENTA,
  retenidaHasta,
} from './textosPagos.js'
import estilos from './CuentaBancaria.module.css'

const FORMULARIO_VACIO = { banco: '', numero: '', declaraTitularidad: false, contrasena: '' }

// HU-18 CA1-CA3: consulta, registro y cambio de la cuenta bancaria del arrendador. El número solo se ve enmascarado
// (lo devuelve así la API) y el formulario no lo conserva tras guardar. La retención de 72 h la aplica el servidor.
function CuentaBancaria() {
  const { sesion } = useSesion()
  const esArrendador = sesion?.rol === 'ARRENDADOR'
  const [datos, setDatos] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [errorCarga, setErrorCarga] = useState(null)
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO)
  const [errores, setErrores] = useState({})
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [exito, setExito] = useState(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!esArrendador) return undefined
    let activo = true
    obtenerCuentaBancaria()
      .then((respuesta) => {
        if (activo) setDatos(respuesta)
      })
      .catch((falla) => {
        if (activo) setErrorCarga(falla.mensaje)
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [esArrendador])

  if (!esArrendador) return <Navigate to="/panel" replace />

  function cambiar(campo, valor) {
    setFormulario((anterior) => ({ ...anterior, [campo]: valor }))
  }

  async function guardar(evento) {
    evento.preventDefault()
    setEnviando(true)
    setErrores({})
    setErrorGeneral(null)
    setExito(null)
    try {
      const cuenta = await guardarCuentaBancaria(formulario)
      setDatos((anterior) => ({ ...anterior, cuenta }))
      setFormulario(FORMULARIO_VACIO)
      setExito(CUENTA_GUARDADA)
    } catch (falla) {
      const porCampo = erroresPorCampo(falla.errores ?? [])
      setErrores(porCampo)
      if (Object.keys(porCampo).length === 0) setErrorGeneral(falla.mensaje)
      setFormulario((anterior) => ({ ...anterior, contrasena: '' }))
    } finally {
      setEnviando(false)
    }
  }

  const cuenta = datos?.cuenta
  const identidadVerificada = Boolean(datos?.identidadVerificada)

  return (
    <section className={estilos.pantalla}>
      <h2>{TITULO_CUENTA_BANCARIA}</h2>
      <p className={estilos.secundario}>{DESCRIPCION_CUENTA_BANCARIA}</p>

      {cargando && <p className={estilos.secundario}>{CARGANDO_CUENTA}</p>}
      {errorCarga && <Alerta tipo="error">{errorCarga}</Alerta>}

      {datos && (
        <>
          <div className={estilos.bloque}>
            {cuenta ? (
              <Tarjeta titulo={cuenta.banco}>
                <div>
                  <EtiquetaEstado estado={cuenta.estado} />
                </div>
                <p className={estilos.dato}>
                  Cuenta: <strong className={estilos.valor}>{cuenta.numeroEnmascarado}</strong>
                </p>
                <p className={estilos.dato}>Titular: {cuenta.titular}</p>
                {cuenta.estado === 'RETENIDA' && cuenta.retenidaHasta && (
                  <p className={estilos.dato}>{retenidaHasta(fechaHora(cuenta.retenidaHasta))}</p>
                )}
              </Tarjeta>
            ) : (
              <p className={estilos.secundario}>{SIN_CUENTA_BANCARIA}</p>
            )}
          </div>

          {exito && <Alerta tipo="exito">{exito}</Alerta>}
          {errorGeneral && <Alerta tipo="error">{errorGeneral}</Alerta>}

          {!identidadVerificada && (
            <Alerta tipo="atencion">
              {AVISO_IDENTIDAD_CUENTA} <Link to="/panel/identidad">{ENLACE_VERIFICAR_IDENTIDAD}</Link>
            </Alerta>
          )}
          {identidadVerificada && cuenta && <Alerta tipo="atencion">{AVISO_CAMBIO_CUENTA}</Alerta>}

          <form className={estilos.formulario} onSubmit={guardar} noValidate>
            <h3 className={estilos.subtitulo}>{TITULO_FORMULARIO_CUENTA}</h3>
            <Campo
              etiqueta={ETIQUETA_BANCO}
              nombre="banco"
              valor={formulario.banco}
              onChange={(valor) => cambiar('banco', valor)}
              error={errores.banco}
            />
            <Campo
              etiqueta={ETIQUETA_NUMERO_CUENTA}
              nombre="numero"
              valor={formulario.numero}
              onChange={(valor) => cambiar('numero', valor)}
              error={errores.numero}
              ayuda={AYUDA_NUMERO_CUENTA}
            />
            <Campo
              tipo="checkbox"
              etiqueta={ETIQUETA_DECLARACION}
              nombre="declaraTitularidad"
              valor={formulario.declaraTitularidad}
              onChange={(valor) => cambiar('declaraTitularidad', valor)}
              error={errores.declaraTitularidad}
            />
            <Campo
              tipo="password"
              etiqueta={ETIQUETA_CONTRASENA_CUENTA}
              nombre="contrasena"
              valor={formulario.contrasena}
              onChange={(valor) => cambiar('contrasena', valor)}
              error={errores.contrasena}
              ayuda={AYUDA_CONTRASENA_CUENTA}
            />
            <Boton tipo="submit" cargando={enviando} deshabilitado={!identidadVerificada}>
              {BOTON_GUARDAR_CUENTA}
            </Boton>
          </form>
        </>
      )}
    </section>
  )
}

export default CuentaBancaria
