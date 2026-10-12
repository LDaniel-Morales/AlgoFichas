# Detección de fichas sobre fondos arbitrarios

Análisis hecho el 2026-09-23 a partir de una prueba real: 5 fichas de cartón (cuadrado rojo,
rectángulos azul/rojo/amarillo, círculo azul) sobre una mesa de madera clara. Solo 1 de 5 se
clasificó bien. Los contornos de las otras 4 tenían extent 0.03–0.17 y solidez 0.04–0.19: el
detector no veía la ficha rellena, sino un trazo de borde abierto alrededor de ella. La causa
es que el pipeline original buscaba bordes solo en **escala de grises**, y en gris una ficha
roja o amarilla sobre madera clara casi no tiene contraste. Las vetas de la madera agregan
bordes falsos y cortan los reales.

## El problema de fondo

Una ficha se detecta si se distingue de lo que la rodea. Hoy la app solo mira **brillo**
(escala de grises), por eso una ficha roja o amarilla sobre madera clara casi desaparece.
Pero cualquier fondo puede fallar de alguna forma:

| Tipo de fondo | Ejemplo | Qué se rompe |
|---|---|---|
| Mismo brillo que la ficha | Madera clara + ficha amarilla | Bordes en gris (tu caso) |
| Mismo color/saturación | Mantel rojo + ficha roja | Segmentar por color |
| Con textura | Vetas de madera, granito, tela | Bordes falsos por todos lados, los reales se cortan |
| Estampado | Mantel a cuadros, periódico | Casi todo |
| Luz dispareja | Sombras, reflejo de lámpara | Umbrales fijos |
| Objetos alrededor | La laptop de la foto | Fichas "fantasma" |

Ningún método único resuelve todas las filas.

## Opciones

**A. Segmentar por saturación** (propuesta inicial)
Buscar zonas "muy coloridas" vs. "apagadas".
- ✅ Simple, rápido, resuelve la madera.
- ❌ Falla con fondos de color intenso y con fichas blancas, grises o pastel.

**B. Bordes por canal de color**
Canny en cada canal de Lab (brillo + dos ejes de color) en lugar de solo en gris, y luego
cerrar huecos.
- ✅ El rojo sobre beige sí da borde en los canales de color aunque no en brillo.
- ❌ La textura sigue metiendo ruido; los contornos pueden seguir abiertos.

**C. Aprender el color del fondo**
Tomar el color dominante de la foto (sobre todo en las orillas) como "fondo" y marcar como
ficha todo lo que se aleje bastante de ese color (distancia en Lab).
- ✅ Se adapta solo a cualquier fondo liso, sea del color que sea. Sin umbrales fijos.
- ❌ Falla con fondos estampados o cuando una ficha es del mismo color que el fondo.

**D. Suavizar la textura antes de todo** (complemento de cualquier opción)
Un filtro que borra las vetas y el grano pero conserva los bordes fuertes
(`pyrMeanShiftFiltering` o filtro bilateral).
- ✅ Ataca directamente el problema de la madera.
- ❌ Agrega tiempo de procesamiento (aceptable en una sola foto).

**E. Varios métodos a la vez y elegir el mejor**
Correr A, B y C en paralelo; cada uno propone contornos; a cada contorno se le da un puntaje
(qué tan sólido, compacto y cerrado es, y si su tamaño es razonable), y se quedan los mejores
sin repetir la misma ficha.
- ✅ Lo más general: si un método falla con cierto fondo, otro lo cubre. Como es una sola
  foto, podemos pagar el costo.
- ❌ Más código y más cosas que calibrar.

**F. Ajustar las fichas o la base**
- **Marcadores ArUco** impresos en cada ficha: casi infalible en cualquier fondo, y además
  dice el tipo de ficha directamente, sin clasificar forma. Costo: hay que imprimirlos y la
  ficha se ve menos artesanal.
- **Borde negro grueso** en cada ficha: contraste garantizado con fondos claros y medios.
- **Base estándar** ("coloca las fichas sobre una hoja blanca"): lo más barato, pero depende
  de que el usuario lo haga.

**G. Modelo entrenado** (YOLO / TFLite)
- ✅ Lo más robusto a largo plazo.
- ❌ Requiere cientos de fotos etiquetadas; es un proyecto aparte. Tiene sentido después,
  no ahora.

**H. Avisar al usuario cuando la foto no sirve**
Si ningún contorno tiene buen puntaje, decir algo como "Fondo con poco contraste, prueba
sobre una superficie lisa". No mejora la detección, pero evita resultados erróneos
silenciosos, que son lo peor para un niño o un docente.

## Propuesta

Combinar **D + E (con A, B y C) + H**: suavizar la textura, generar candidatos con los tres
métodos, elegir los mejores, y avisar cuando la foto no sirve. Es lo más general que podemos
lograr solo con OpenCV y sin tocar las fichas. Dejar **F** (ArUco o borde) como plan B si aun
así hay fondos que fallen, y **G** para el futuro.

Para iterar sin adivinar, se propuso además que el modo debug **guarde cada foto capturada**
en el teléfono, para descargarla con `adb pull` y probar las opciones en la PC con
Python + OpenCV sobre las mismas fotos, sin recompilar la app en cada intento.

## Decisión (2026-09-23)

- Se adopta **D + E + H**.
- Las fichas pueden ser **de cualquier color**, en tonos fuertes o suaves: la saturación (A)
  sola no alcanza, por eso la distancia al color del fondo (C) es clave.
- Las fichas son **recortes sencillos, sin marcas ni bordes**: F queda descartada.
- Se prueba primero con el sistema de varios métodos usando el sistema de logs (`ShapeDebug`).
  Guardar las fotos para analizarlas en PC queda para después.
- Objetivo de esta etapa: que la app sea **aparentemente funcional** con el parche más
  sencillo posible. La robustez completa queda para una etapa posterior.
