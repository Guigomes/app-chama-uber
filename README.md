# NotiVault (Android Nativo)

Aplicação Android nativa (Kotlin) que:

- captura notificações do celular em segundo plano via `NotificationListenerService`;
- salva localmente em banco interno com Room;
- ao abrir o app, mostra lista de notificações ordenadas da mais recente para a mais antiga;
- quando chega notificação do WhatsApp da Suely Férias com a palavra `uber`, abre a Uber por deep link com rota pré-preenchida.

Endereços fixos usados no gatilho:

- Origem: Rua Serra Azul, 780. Campo Grande
- Destino: Rua Guararapes, 174. Coophamat - Campo Grande

Quando o deep link é disparado, o app emite uma notificação local "Uber chamada".

## Requisitos

- Android Studio (Hedgehog ou superior)
- SDK Android 34 instalado
- Dispositivo Android físico com depuração USB ativa

## Como rodar no celular (sem Expo)

1. Conecte o celular via USB e habilite **Depuração USB**.
2. Abra este projeto no Android Studio.
3. Aguarde o **Gradle Sync** finalizar.
4. Clique em **Run** e selecione o dispositivo físico.
5. No app, toque em **Ativar acesso** e habilite o app em **Acesso a notificações**.
6. Gere notificações no celular (WhatsApp, Gmail, etc.) e volte ao app para ver a lista.

## Estrutura principal

- `app/src/main/java/com/defy/notivault/service/NotificationCaptureService.kt`: escuta notificações do sistema.
- `app/src/main/java/com/defy/notivault/data/*`: entidade, DAO e banco Room.
- `app/src/main/java/com/defy/notivault/MainActivity.kt`: tela inicial com lista ordenada.

## Observações

- O app só salva notificações após o usuário habilitar o acesso.
- Notificações já antigas (antes da permissão) não são importadas retroativamente.
- O fluxo usa deep link; não depende de credenciais OAuth da Uber.
