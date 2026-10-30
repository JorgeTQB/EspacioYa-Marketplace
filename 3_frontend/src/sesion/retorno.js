const RUTA_INTERNA = /^\/(?![/\\])/

// Solo una ruta de este sitio: una sola barra al inicio. "//host" y "/\host" (el navegador lee la barra invertida como
// "/") apuntan a otro origen, y history.replaceState lanzaría SecurityError. null = no usar este retorno.
export function retornoValido(ruta) {
  return typeof ruta === 'string' && RUTA_INTERNA.test(ruta) ? ruta : null
}
