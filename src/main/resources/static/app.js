// Comportamentos simples das páginas: confirmação de exclusão, aviso temporário,
// validação do login e cadastro rápido de categoria/fonte sem sair do formulário.
(function () {
	const $ = s => document.querySelector(s);

	// Modo claro (padrão) / modo escuro: troca o atributo data-theme e guarda a escolha.
	function mostrarTema() {
		const claro = document.documentElement.dataset.theme !== 'dark';
		document.querySelectorAll('[data-tema-escuro]').forEach(e => e.hidden = claro);
		document.querySelectorAll('[data-tema-claro]').forEach(e => e.hidden = !claro);
		document.querySelectorAll('[data-tema]').forEach(b => b.setAttribute('aria-pressed', String(claro)));
	}
	document.querySelectorAll('[data-tema]').forEach(b => b.addEventListener('click', () => {
		const novo = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
		document.documentElement.dataset.theme = novo;
		try { localStorage.setItem('tema', novo); } catch (e) {}
		mostrarTema();
		mostrarAviso(novo === 'dark' ? 'Modo escuro ativado' : 'Modo claro ativado');
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

	// Checkbox de pagamento: ao marcar/desmarcar, envia o formulário da própria parcela
	// (com o token CSRF) e volta para a mesma posição da lista.
	document.querySelectorAll('[data-pagar]').forEach(caixa => caixa.addEventListener('change', () => {
		try { sessionStorage.setItem('rolagem', String(window.scrollY)); } catch (e) {}
		caixa.closest('label').style.opacity = '.6';
		caixa.form.submit();
	}));
	try {
		const y = sessionStorage.getItem('rolagem');
		if (y !== null) { sessionStorage.removeItem('rolagem'); window.scrollTo(0, parseInt(y, 10) || 0); }
	} catch (e) {}

	// Edição de parcela: se o valor mudou e a compra tem outras parcelas pendentes,
	// pergunta se o novo valor vale também para elas (Sim/Não) antes de salvar.
	// Precisa vir antes do "enviar uma vez" para que o botão não fique desativado.
	const dlgParcelas = $('#dlg-demais-parcelas'), formDespesa = $('#form-despesa'), aplicar = $('#aplicarDemaisPendentes');
	if (dlgParcelas && formDespesa && aplicar && formDespesa.dataset.valorOriginal) {
		const centavosDe = v => Math.round(parseFloat(String(v).replace(',', '.')) * 100);
		let decidido = false;
		formDespesa.addEventListener('submit', e => {
			if (decidido) return;
			const novo = centavosDe($('#valor').value);
			if (!(novo > 0) || novo === centavosDe(formDespesa.dataset.valorOriginal)) { aplicar.value = 'false'; return; }
			e.preventDefault();
			dlgParcelas.querySelector('[data-novo-valor]').textContent =
				(novo / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
			dlgParcelas.showModal();
		});
		dlgParcelas.querySelectorAll('[data-aplicar]').forEach(b => b.addEventListener('click', () => {
			aplicar.value = b.dataset.aplicar;
			decidido = true;
			dlgParcelas.close();
			if (formDespesa.requestSubmit) formDespesa.requestSubmit(); else formDespesa.submit();
		}));
		// Fechar (X, Esc ou clique fora) cancela: nada é salvo e o formulário continua aberto.
		dlgParcelas.querySelectorAll('[data-fechar]').forEach(b => b.addEventListener('click', () => dlgParcelas.close()));
		dlgParcelas.addEventListener('click', e => { if (e.target === dlgParcelas) dlgParcelas.close(); });
	}

	// Evita clique duplo no "Salvar" (o servidor também ignora o mesmo formulário enviado duas vezes).
	document.querySelectorAll('[data-enviar-uma-vez]').forEach(botao => botao.form.addEventListener('submit', e => {
		if (e.defaultPrevented) return;
		setTimeout(() => botao.disabled = true, 0);
	}));

	// Prévia das parcelas: "3x: 1x de R$ 33,34 e 2x de R$ 33,33".
	const qtd = $('#quantidadeParcelas'), valor = $('#valor'), previa = $('#previa-parcelas');
	if (qtd && valor && previa) {
		const brl = c => (c / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
		const atualizar = () => {
			const n = parseInt(qtd.value, 10), centavos = Math.round(parseFloat(String(valor.value).replace(',', '.')) * 100);
			if (!(n >= 1) || !(centavos > 0)) { previa.textContent = '1 = à vista'; return; }
			if (n === 1) { previa.textContent = 'À vista: ' + brl(centavos); return; }
			const base = Math.floor(centavos / n), resto = centavos % n;
			if (base < 1) { previa.textContent = 'Valor baixo demais para ' + n + ' parcelas'; return; }
			previa.textContent = resto === 0 ? n + 'x de ' + brl(base)
				: n + 'x: ' + resto + 'x de ' + brl(base + 1) + ' e ' + (n - resto) + 'x de ' + brl(base);
		};
		qtd.addEventListener('input', atualizar); valor.addEventListener('input', atualizar); atualizar();
	}

	function mostrarAviso(texto) {
		let el = $('#toast');
		if (!el) { el = document.createElement('div'); el.id = 'toast'; el.setAttribute('role', 'status'); document.body.append(el); }
		el.textContent = texto; el.hidden = false;
		clearTimeout(mostrarAviso.t); mostrarAviso.t = setTimeout(() => el.hidden = true, 3500);
	}
})();
