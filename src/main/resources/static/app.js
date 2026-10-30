// Comportamentos simples das páginas: confirmação de exclusão, aviso temporário,
// validação do login e cadastro rápido de categoria/fonte sem sair do formulário.
(function () {
	const $ = s => document.querySelector(s);

	// Modo escuro / modo claro: troca o atributo data-theme e guarda a escolha.
	function mostrarTema() {
		const claro = document.documentElement.dataset.theme === 'light';
		document.querySelectorAll('[data-tema-escuro]').forEach(e => e.hidden = claro);
		document.querySelectorAll('[data-tema-claro]').forEach(e => e.hidden = !claro);
		document.querySelectorAll('[data-tema]').forEach(b => b.setAttribute('aria-pressed', String(claro)));
	}
	document.querySelectorAll('[data-tema]').forEach(b => b.addEventListener('click', () => {
		const novo = document.documentElement.dataset.theme === 'light' ? 'dark' : 'light';
		document.documentElement.dataset.theme = novo;
		try { localStorage.setItem('tema', novo); } catch (e) {}
		mostrarTema();
	}));
	mostrarTema();

	// Pede confirmação antes de enviar formulários de exclusão.
	document.addEventListener('submit', e => {
		const msg = e.target.dataset.confirmar;
		if (msg && !confirm(msg)) e.preventDefault();
	});

	// Esconde o aviso de sucesso/erro depois de alguns segundos.
	const toast = $('#toast');
	if (toast) setTimeout(() => toast.hidden = true, 4500);

	// Login: avisa campos vazios antes de enviar.
	const login = $('#form-login');
	if (login) {
		login.addEventListener('submit', e => {
			let ok = true;
			login.querySelectorAll('[data-obrigatorio]').forEach(campo => {
				const erro = document.getElementById(campo.id + '-erro');
				const vazio = !campo.value.trim();
				erro.textContent = vazio ? campo.dataset.obrigatorio : '';
				campo.classList.toggle('invalido', vazio);
				if (vazio && ok) { campo.focus(); ok = false; }
			});
			if (!ok) e.preventDefault();
		});
	}

	// Cadastro rápido: abre o diálogo, envia para a API e já seleciona o item criado.
	document.querySelectorAll('[data-rapido]').forEach(botao => {
		const dialogo = document.getElementById(botao.dataset.rapido);
		const form = dialogo.querySelector('form');
		const select = document.getElementById(form.dataset.select);
		const erro = form.querySelector('.erro');

		botao.addEventListener('click', () => { form.reset(); erro.textContent = ''; dialogo.showModal(); form.querySelector('input').focus(); });
		dialogo.querySelectorAll('[data-fechar]').forEach(b => b.addEventListener('click', () => dialogo.close()));
		dialogo.addEventListener('click', e => { if (e.target === dialogo) dialogo.close(); });

		form.addEventListener('submit', async e => {
			e.preventDefault();
			const dados = {};
			new FormData(form).forEach((v, k) => dados[k] = k === 'limiteMensal' ? parseFloat(String(v).replace(',', '.')) || 0 : v);
			try {
				const resp = await fetch(form.dataset.api, {
					method: 'POST', credentials: 'same-origin',
					headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
					body: JSON.stringify(dados)
				});
				const corpo = await resp.json().catch(() => ({}));
				if (!resp.ok) { erro.textContent = corpo.erro || 'Não foi possível salvar. Confira os campos.'; return; }
				const opcao = new Option(corpo.nome, corpo.id, true, true);
				select.add(opcao);
				select.classList.remove('invalido');
				dialogo.close();
				mostrarAviso(form.dataset.sucesso.replace('{nome}', corpo.nome));
			} catch (falha) {
				erro.textContent = 'Sem conexão com o servidor. Tente novamente.';
			}
		});
	});

	function mostrarAviso(texto) {
		let el = $('#toast');
		if (!el) { el = document.createElement('div'); el.id = 'toast'; el.setAttribute('role', 'status'); document.body.append(el); }
		el.textContent = texto; el.hidden = false;
		clearTimeout(mostrarAviso.t); mostrarAviso.t = setTimeout(() => el.hidden = true, 3500);
	}
})();
