import { useEffect, useRef, useState } from 'react'
import estilos from './CuentaRegresiva.module.css'

const SEGUNDOS_POR_MINUTO = 60
const MILISEGUNDOS_POR_SEGUNDO = 1000

function dosDigitos(numero) {
  return String(numero).padStart(2, '0')
}

function mmss(segundos) {
  return `${dosDigitos(Math.floor(segundos / SEGUNDOS_POR_MINUTO))}:${dosDigitos(segundos % SEGUNDOS_POR_MINUTO)}`
}

// Calcula el fin al montarse y compara contra Date.now(): en una pestaña en segundo plano el intervalo se atrasa,
// pero el contador no. Para reiniciarlo con otros segundos, cámbiale la key.
function CuentaRegresiva({ segundos, alTerminar }) {
  const [fin] = useState(() => Date.now() + segundos * MILISEGUNDOS_POR_SEGUNDO)
  const [restantes, setRestantes] = useState(segundos)
  const avisar = useRef(alTerminar)

  useEffect(() => {
    avisar.current = alTerminar
  }, [alTerminar])

  useEffect(() => {
    const intervalo = setInterval(() => {
      const faltan = Math.max(0, Math.ceil((fin - Date.now()) / MILISEGUNDOS_POR_SEGUNDO))
      setRestantes(faltan)
      if (faltan === 0) {
        clearInterval(intervalo)
        avisar.current?.()
      }
    }, MILISEGUNDOS_POR_SEGUNDO)
    return () => clearInterval(intervalo)
  }, [fin])

  return (
    <span role="timer" className={estilos.contador}>
      {mmss(restantes)}
    </span>
  )
}

export default CuentaRegresiva
