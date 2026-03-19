# jobhunter_ai

Projeto inicial (expandivel) para:
- buscar vagas (crawler)
- armazenar no SQLite
- calcular compatibilidade via OpenAI
- expor API via FastAPI
- oferecer "apply preview" seguro (sem envio automatico)

Importante: automacao de candidatura / auto-apply em sites de terceiros pode violar termos e virar spam. Este projeto foi feito para manter o usuario no controle, com confirmacao humana.

## Rodar (Windows / PowerShell)

1) Configure variaveis de ambiente (exemplo):

```powershell
$env:OPENAI_API_KEY="SUA_CHAVE"
$env:JOBHUNTER_ALLOW_NETWORK_CRAWLING="true"
```

2) Rode:

```powershell
./run_all.ps1
```

API: `http://127.0.0.1:8000`

Teste:
- `GET /health`
- `GET /buscar?keyword=dados&limit=10`

## Fontes (crawlers)

Por padrao:  ou 'indeed,nerdin,mentoradados,vagascom'.

Exemplo:
- GET /buscar?keyword=dados&sources=indeed,vagascom

Gupy precisa de paginas de carreira por empresa:
- ='https://suaempresa.gupy.io/,https://outra.gupy.io/'

LinkedIn/Catho estao desativados (login/termos).


## Erro PowerShell (Host)
Se voce viu 'Cannot overwrite variable Host', use o parametro novo do script:
- ./run_all.ps1 -BindHost 0.0.0.0
