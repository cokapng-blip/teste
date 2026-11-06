(function () {
  var n = 0;
  var contador = document.getElementById('contador');
  document.getElementById('btn').addEventListener('click', function () {
    n++;
    contador.textContent = String(n);
  });
  fetch('data.json').then(function (r) { return r.json(); }).then(function (d) {
    var ul = document.getElementById('produtos');
    ul.innerHTML = '';
    d.produtos.forEach(function (p) {
      var li = document.createElement('li');
      li.textContent = p.nome + ' — ' + p.preco + ' esmeraldas';
      ul.appendChild(li);
    });
  });
  try { localStorage.setItem('visitas', String((+localStorage.getItem('visitas') || 0) + 1)); } catch (e) {}
  document.getElementById('externo').addEventListener('click', function () {
    var s = document.getElementById('status-externo');
    s.textContent = 'tentando…';
    fetch('https://example.com/').then(function () { s.textContent = 'PERMITIDO'; })
      .catch(function () { s.textContent = 'bloqueado'; });
  });
})();
