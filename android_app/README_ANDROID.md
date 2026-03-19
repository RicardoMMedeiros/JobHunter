# Android UI (Compose)

Esta pasta contem os arquivos Kotlin prontos para voce colar em um projeto Android Studio.

## 1) Crie um projeto
- Android Studio -> New Project -> Empty Activity (Jetpack Compose)
- Package: com.jobhunter.ai

## 2) Cole os arquivos
Cole todos os arquivos de `jobhunter_ai/android_app` em `app/src/main/java/com/jobhunter/ai/`.

## 3) Dependencias (app/build.gradle)
Adicione (ou confirme):
- OkHttp
- WorkManager
- Coroutines
- ViewModel Compose

Exemplo (Groovy):
- implementation "com.squareup.okhttp3:okhttp:4.12.0"
- implementation "androidx.work:work-runtime-ktx:2.9.0"
- implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
- implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2"

## 4) Manifest
- <uses-permission android:name="android.permission.INTERNET" />
- Para http:// (emulador): android:usesCleartextTraffic="true" no <application>

## 5) Backend
- Emulador: base URL = http://10.0.2.2:8000
- Celular: rode backend com -BindHost 0.0.0.0 e use http://IP_DO_PC:8000

## 6) O que a UI faz
- Buscar: configura URL/keyword/local/limite e escolhe fontes; mostra erros por fonte
- Resultados/Salvas: lista vagas, tem Detalhes, Preview apply (seguro) e Abrir no navegador
- Match: cola curriculo + descricao e calcula score

Obs: este app nao envia candidatura automaticamente.
