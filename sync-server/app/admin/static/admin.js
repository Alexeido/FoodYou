// Tooltip compartido para las gráficas: cualquier elemento con data-tip lo
// enseña al pasar el ratón o al tocarlo. Sin esto el panel funciona igual; las
// tablas de cada gráfica tienen los mismos datos.
(() => {
  const tip = document.createElement("div");
  tip.className = "tip";
  tip.setAttribute("role", "tooltip");
  document.body.appendChild(tip);

  let current = null;

  function place(x, y) {
    const r = tip.getBoundingClientRect();
    let left = x + 14;
    let top = y - r.height - 12;
    if (left + r.width > window.innerWidth - 8) left = x - r.width - 14;
    if (top < 8) top = y + 18;
    tip.style.left = `${Math.max(8, left)}px`;
    tip.style.top = `${top}px`;
  }

  function show(el, x, y) {
    current = el;
    tip.textContent = el.dataset.tip;
    tip.classList.add("show");
    place(x, y);
  }

  function hide() {
    current = null;
    tip.classList.remove("show");
  }

  document.addEventListener("pointermove", (e) => {
    const el = e.target.closest("[data-tip]");
    if (!el) return current && hide();
    if (el !== current) show(el, e.clientX, e.clientY);
    else place(e.clientX, e.clientY);
  });
  document.addEventListener("pointerleave", hide);
  document.addEventListener("scroll", hide, { passive: true });

  // Formularios que piden confirmación (borrar una cuenta).
  document.addEventListener("submit", (e) => {
    const msg = e.target.dataset.confirm;
    if (msg && !window.confirm(msg)) e.preventDefault();
  });
})();
