# Testes funcionais do Finora

Registro da validação funcional feita em 13/11/2026, no fuso America/Sao_Paulo, a partir da branch `validacao-funcional` (criada da `main` no commit do merge do PR #2).

## Ambiente

- Sistema operacional: Windows 11 Home Single Language (versão 10.0.26200)
- Projeto: Java 21 (Spring Boot 4.1.1)
- Runtime usado nesta validação: Java 26 (build 26+35-2893)
- Banco local: H2 em arquivo, `./data/controle-gastos`, porta 8080
- Perfil: desenvolvimento (sem `MAIL_API_KEY`). Os códigos de confirmação e de recuperação de senha aparecem no log `app-local.log`
- Cópia de segurança do banco, feita antes de subir a aplicação: `data-backup-20261113-163038-antes-validacao` (pasta local, fora do Git)
- Contas usadas: apenas contas novas de teste (`validacao.1639`, `validacao.limite.1639` e `validacao.fluxo.1641`). Nenhuma conta já existente foi alterada

## Como executar

No Windows, na pasta do repositório:

```
mvnw.cmd test
mvnw.cmd spring-boot:run
```

A aplicação sobe em http://localhost:8080. A tela de login é `/login`.

Os testes funcionais desta validação foram feitos por HTTP (GET e POST com cookie de sessão e token CSRF), com `curl.exe`. As capturas das telas públicas (`/login`, `/cadastro` e `/senha/esqueci`) foram geradas com o Microsoft Edge em modo headless. Senhas e códigos de verificação foram mascarados nas evidências.

As evidências estão em [`evidencias/`](evidencias/).

## Resultado da inicialização

A aplicação iniciou corretamente.

Trecho do log:

```
2026-11-13T16:36:37.690-03:00  INFO ... o.s.boot.tomcat.TomcatWebServer : Tomcat initialized with port 8080 (http)
2026-11-13T16:36:45.092-03:00  INFO ... o.s.boot.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path '/'
2026-11-13T16:36:45.101-03:00  INFO ... b.c.c.ControleGastosApplication : Started ControleGastosApplication in 10.876 seconds (process running for 11.852)
```

- Tempo até `Started ControleGastosApplication`: 10,876 segundos
- `GET /login`: HTTP 200
- Evidências: [00-inicializacao.txt](evidencias/00-inicializacao.txt) e captura [01-login.png](evidencias/01-login.png)

## Resultado dos testes automáticos

Comando: `mvnw.cmd test` (antes de subir a aplicação, para não disputar a porta 8080).

```
Tests run: 105, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time: 01:02 min
Finished at: 2026-11-13T16:31:52-03:00
```

Todos os 105 testes automáticos passaram.

## Testes funcionais

Resumo:

| Teste | Situação |
| --- | --- |
| Teste 01 - Cadastro com confirmação de e-mail e login | passou |
| Teste 02 - Login com senha errada | passou |
| Teste 03 - Categoria com limite e despesa que ultrapassa o limite | passou |
| Teste 04 - Receita fixa, despesas fixas e saldo projetado | passou |
| Teste 05 - Esqueci minha senha (usuário e e-mail) | passou |
| Teste 06 - Nome de usuário com @ e nome duplicado | passou |

### Teste 01 - Cadastro com confirmação de e-mail e login

Entrada/ação: criação da conta `validacao.1639` (e-mail `validacao.1639@exemplo.com`) em `POST /cadastro`; leitura do código de 6 dígitos no log local; `POST /confirmar`; `POST /login` com o nome de usuário e a senha correta; `GET /painel`.

Resultado esperado: o cadastro redireciona para `/confirmar`; o código confirma o e-mail e redireciona para `/login`; o login redireciona para `/painel`, que abre com HTTP 200 e mostra `@validacao.1639`.

Resultado obtido: `POST /cadastro` HTTP 302 para `/confirmar?login=validacao.1639%40exemplo.com`; o log registrou o envio em modo desenvolvimento (código mascarado na evidência); `POST /confirmar` HTTP 302 para `/login`; `POST /login` HTTP 302 para `/painel`; `GET /painel` HTTP 200, com o bloco "Saldo projetado ao final de novembro de 2026" e o `@validacao.1639` no menu.

Situação: passou

Evidências: [teste-01-cadastro-login.txt](evidencias/teste-01-cadastro-login.txt) e [02-cadastro.png](evidencias/02-cadastro.png)

### Teste 02 - Login com senha errada

Entrada/ação: `POST /login` com o usuário válido `validacao.1639` e uma senha incorreta, em uma sessão nova.

Resultado esperado: a autenticação falha; o sistema redireciona para `/login?erro` e a tela exibe o aviso de e-mail ou senha inválidos, sem entrar no painel.

Resultado obtido: HTTP 302 para `http://localhost:8080/login?erro`; `GET` da tela de erro HTTP 200, com o aviso "E-mail ou senha inválidos".

Situação: passou

Evidência: [teste-02-login-senha-errada.txt](evidencias/teste-02-login-senha-errada.txt)

### Teste 03 - Categoria com limite e despesa que ultrapassa o limite

Entrada/ação: na conta nova `validacao.limite.1639`, `POST /categorias` criou "Validacao Limite" com limite mensal de R$ 50,00; em seguida, `POST /despesas` registrou "Gasto acima do limite" de R$ 60,00 nessa categoria, com data 13/11/2026; por fim, `GET /painel`.

Resultado esperado: a categoria aparece com limite de R$ 50,00; a despesa é gravada; o painel exibe alerta de limite estourado para essa categoria.

Resultado obtido: `POST /categorias` HTTP 302; a lista mostrou a categoria com R$ 50,00; `POST /despesas` HTTP 302 para `/despesas?mes=2026-11`; `GET /painel` HTTP 200, com alerta `ESTOURADO` e o texto "Validacao Limite passou do limite do mês: R$ 60,00 de R$ 50,00."

Situação: passou

Evidência: [teste-03-limite-categoria.txt](evidencias/teste-03-limite-categoria.txt)

### Teste 04 - Receita fixa, despesas fixas e saldo projetado

Entrada/ação: na conta nova `validacao.fluxo.1641`, cadastro de uma receita fixa de R$ 300,00 ("Mesada validacao") e de duas despesas fixas de R$ 100,00 ("Aluguel validacao" e "Transporte validacao"), todas com data 13/11/2026; em seguida, `GET /painel`.

Resultado esperado: o saldo projetado ao final do mês é R$ 100,00 (R$ 300,00 de receita menos R$ 100,00 e R$ 100,00 de despesas).

Resultado obtido: os três cadastros retornaram HTTP 302. O painel (HTTP 200) mostrou, no bloco de projeção: receitas + R$ 300,00, despesas pendentes - R$ 200,00 e resultado R$ 100,00, com o texto "R$ 100,00 disponíveis ao final do mês".

Situação: passou

Observação: a primeira execução deste teste falhou no script de coleta, que não lia o campo "fonte" por causa da quebra de linha no HTML. O formulário tinha fontes cadastradas. Depois do ajuste do script, o teste foi reexecutado e passou. Não houve mudança no código da aplicação.

Evidências: [teste-04-saldo-projetado.txt](evidencias/teste-04-saldo-projetado.txt) e [teste-04-painel-trecho.html](evidencias/teste-04-painel-trecho.html)

### Teste 05 - Esqueci minha senha (usuário e e-mail)

Entrada/ação: `GET /senha/esqueci`; `POST /senha/esqueci` com o nome de usuário `validacao.1639` e o e-mail cadastrado `validacao.1639@exemplo.com`; leitura do código no log; `POST /senha/redefinir` com a nova senha; tentativa de login com a senha antiga e, depois, com a nova.

Resultado esperado: o pedido redireciona para a tela de redefinição; a senha é alterada; a senha antiga deixa de funcionar; a senha nova entra no painel.

Resultado obtido: `POST /senha/esqueci` HTTP 302 para `/senha/redefinir?login=validacao.1639`; o log registrou o código em modo desenvolvimento (mascarado na evidência); `POST /senha/redefinir` HTTP 302 para `/login`; login com a senha antiga HTTP 302 para `/login?erro`; login com a senha nova HTTP 302 para `/painel`.

Situação: passou

Evidências: [teste-05-esqueci-senha.txt](evidencias/teste-05-esqueci-senha.txt) e [03-esqueci-senha.png](evidencias/03-esqueci-senha.png)

### Teste 06 - Nome de usuário com @ e nome duplicado

Entrada/ação: `POST /cadastro` com o nome de usuário `nao@valido`; em seguida, `POST /cadastro` com o nome já usado `validacao.1639` e um e-mail novo.

Resultado esperado: as duas tentativas são recusadas e a conta não é criada. A primeira volta à tela de cadastro pedindo um nome sem @. A segunda informa que o nome de usuário já está em uso.

Resultado obtido: ambos os pedidos retornaram HTTP 200 na própria tela de cadastro (sem redirecionar para `/confirmar`). Mensagens exibidas: "O nome de usuário deve ter de 3 a 30 caracteres: letras sem acento, números, ponto, hífen ou sublinhado (sem @ e sem espaços)." e "Esse nome de usuário já está em uso."

Situação: passou

Evidência: [teste-06-usuario-invalido-duplicado.txt](evidencias/teste-06-usuario-invalido-duplicado.txt)

## Correções realizadas

Nenhuma correção no código da aplicação foi necessária. Os seis testes funcionais passaram, e os 105 testes automáticos também.

O único ajuste feito foi no script usado para coletar o Teste 04 (leitura do campo fonte no HTML), descrito nesse teste. Esse script não faz parte do repositório.

## Pendências

- O envio real de e-mail em produção (serviço Brevo) não fez parte desta validação local. Aqui os códigos foram lidos no log, em modo desenvolvimento. Vale incluir um envio verdadeiro na próxima rodada de validações em produção.
- Não houve falha funcional pendente de correção neste ciclo.
