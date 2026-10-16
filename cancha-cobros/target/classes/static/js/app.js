/* JavaScript mínimo: al tocar un nombre se envía el cambio al servidor (fetch) y se actualiza
   la pantalla SIN recargar. Así marcar a 20 personas toma segundos. */

/** POST sin cuerpo; devuelve el JSON o lanza un error con el mensaje del servidor. */
async function post(url) {
  const r = await fetch(url, { method: 'POST' });
  const datos = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(datos.error || 'No se pudo completar la acción.');
  return datos;
}

const soles = (n) => (n === null || n === undefined) ? '—' : 'S/ ' + Number(n).toFixed(2);
const $ = (id) => document.getElementById(id);

/* ---- Compartir link de solo lectura por WhatsApp ---------------------------------------- */
document.querySelectorAll('.js-compartir').forEach((btn) => {
  btn.addEventListener('click', () => {
    const link = location.origin + '/v/' + btn.dataset.token;
    const texto = 'Cobro de la cancha ⚽ Aquí ves quién ya pagó: ' + link;
    window.open('https://wa.me/?text=' + encodeURIComponent(texto), '_blank');
  });
});

/* ---- Pantalla LISTA: marcar presentes --------------------------------------------------- */
document.querySelectorAll('.js-presente').forEach((btn) => {
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    try {
      const d = await post(btn.dataset.url);
      btn.classList.toggle('on', d.activo);
      $('contador').textContent = d.presentes;
      $('cuota-est').textContent = soles(d.cuotaEstimada);
    } catch (e) {
      alert(e.message);
    } finally {
      btn.disabled = false;
    }
  });
});

/* ---- Pantalla COBRANDO: marcar pagos ---------------------------------------------------- */
function actualizarResumen(d) {
  $('cobrado').textContent = soles(d.cobrado);
  $('esperado').textContent = soles(d.esperado);
  $('sobrante').textContent = soles(d.sobranteReal);
  $('pendientes-n').textContent = d.pendientes;
  $('barra').style.width = d.porcentaje + '%';
  $('n-pendientes').textContent = $('lista-pendientes').children.length;
  $('n-pagados').textContent = $('lista-pagados').children.length;
  const reabrir = $('form-reabrir');               // solo se puede reabrir si nadie ha pagado
  if (reabrir) reabrir.style.display = d.pagaron > 0 ? 'none' : '';
}

document.querySelectorAll('.js-pago').forEach((btn) => {
  btn.addEventListener('click', async () => {
    btn.disabled = true;
    try {
      const d = await post(btn.dataset.url);
      const li = btn.closest('li');
      btn.classList.toggle('pagado', d.activo);
      btn.querySelector('.accion').textContent = d.activo ? '✓ Pagó' : 'Cobrar';
      // El nombre "salta" a la otra lista: de pendientes a pagados (o al revés si te equivocaste).
      $(d.activo ? 'lista-pagados' : 'lista-pendientes').appendChild(li);
      actualizarResumen(d);
    } catch (e) {
      alert(e.message);
    } finally {
      btn.disabled = false;
    }
  });
});

/* ---- Cerrar partido: avisa si aún faltan pagos ------------------------------------------ */
function confirmarCierre() {
  const faltan = parseInt($('pendientes-n').textContent, 10);
  return faltan === 0 || confirm('Aún faltan ' + faltan + ' por pagar. ¿Cerrar el partido igual?');
}
