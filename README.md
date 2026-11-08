# Estudo TV

App Android TV da família: abre o Portal de Estudo e os jogos do Sótão 57 na TV da sala, em ecrã inteiro, com o comando da TV ou um comando PlayStation.

Este repositório tem **só o código da app**. Não tem dados, palavras-passe nem conteúdo do portal.

## Instalar na TV
Ver [`docs/instalar-na-tv.md`](docs/instalar-na-tv.md). Resumo: na app Downloader, escrever o endereço curto que aponta para
`https://github.com/fplamorim/estudo-tv/releases/latest/download/estudo-tv.apk`.

## O que a app faz
- Abre o portal (`fernandoamorim.com/estudo/`). O login é o do próprio portal (QR lido no telemóvel dos pais). A sessão fica no cookie da WebView, na pasta privada da app.
- Só navega no portal e em `sotao57.com/pt/`, `/br/`, `/us/` e `/games/`. Bloqueia o resto: ligações externas, pagamentos e e-mail.
- WebView sem Safe Browsing e sem métricas. Não usa Google Play Services, Firebase nem estatísticas.
- **Comando:** setas/direcional = mover, OK/✕ = escolher, Voltar/○ = voltar. O botão PS, a tecla Menu ou Voltar mantido abrem o menu da app (Início, Sair / trocar de jogador, Procurar atualizações, Sobre). Nos jogos do Sótão 57, os botões do comando passam diretos para o jogo.
- **Ecrã:** fica ligado enquanto se usa a app. Ao fim de 30 minutos sem teclas, a proteção de ecrã da TV volta a funcionar.
- **Voz:** `EstudoTV.falar(texto)` para o portal usar a voz do próprio Android (pt-PT).
- **Atualizações:** ao abrir (no máximo de 6 em 6 horas), consulta a API pública de Releases deste repositório. Se houver versão nova, pergunta, descarrega, confirma o SHA-256 e abre o instalador do Android.
- **Stage escondido:** em «Sobre», carregar 5 vezes OK no logótipo. As versões 0.x arrancam no Stage.

## Publicar uma versão
1. Em Actions → Release → Run workflow, escrever a versão (ex.: `0.1.1`) e as notas. Também se pode enviar uma tag `vX.Y.Z`.
2. O workflow `release.yml` compila, assina com a keystore dos segredos (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) e publica `estudo-tv.apk` na Release.

A keystore nunca entra no repositório. Se se perder, a app instalada deixa de poder ser atualizada.

## Compilar no computador
Precisa do JDK 21 e do Android SDK (API 36): `./gradlew assembleDebug`.
A arte (banner, ícone, logótipo) é gerada por `python3 tools/arte.py`.

## Licença
MIT.
