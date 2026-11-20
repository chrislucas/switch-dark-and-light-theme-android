package com.br.switchdarkandlighttheme.core.navigation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.core.net.toUri

/**
 * Resolve um deeplink (ex.: `switchthemeeffect://home`) para uma Activity DESTE app
 * e, opcionalmente, abre essa Activity.
 *
 * Origem: versão unificada de duas implementações antigas
 * - `SampleCustomView/intentnavigation/.../ResolverDeepLink.kt` (redirectByDeeplink,
 *   redirectByFirstFromDeepLink, queryIntentActivities);
 * - `FunWithDataBinding/navfeatures/.../StartActivityByDeeplink.kt` (mesma ideia, com
 *   suporte a `ResolveInfoFlags` do Android 13 e callback de erro).
 *
 * Como funciona:
 * 1. O texto vira um `Intent(ACTION_VIEW, uri)` — é exatamente o que o sistema monta
 *    quando alguém abre o link; os `<intent-filter>` do AndroidManifest decidem quem
 *    o atende.
 * 2. O Intent é restrito ao nosso pacote (`setPackage`), e o PackageManager responde
 *    quais Activities atendem a ele.
 * 3. Com a resposta, o Intent vira EXPLÍCITO (com o componente encontrado) e pode ser
 *    aberto com `startActivity` sem ambiguidade.
 *
 * Melhorias em relação às versões antigas:
 * - **Os dados do link chegam ao destino.** As versões antigas criavam um `Intent()`
 *   novo só com o componente, perdendo a `Uri` (path, query params). Aqui o mesmo
 *   Intent é reaproveitado, então a Activity pode ler `intent.data`.
 * - **Flags no lugar certo.** As antigas passavam `FLAG_ACTIVITY_*` para
 *   `Intent.parseUri(uri, flags)`, que espera flags de PARSING (`URI_INTENT_SCHEME`,
 *   `URI_ALLOW_UNSAFE`…). As flags de Activity eram ignoradas. Aqui elas vão para o
 *   Intent que é aberto.
 * - **Sem `URI_ALLOW_UNSAFE` / `intent:`.** Aceitar `intent:` URIs a partir de texto
 *   permite que um link escolha qualquer componente, extras e flags (risco de
 *   "intent redirection"). Só tratamos links comuns (`esquema://...`).
 * - **Só Activities do próprio app.** Sem `setPackage`, o link podia resolver para
 *   outro app ou para o seletor do sistema (`ResolverActivity`), que não pode ser
 *   aberto como componente explícito.
 * - **`redirectByFirstFromDeepLink` não abria nada**: montava o Intent e o descartava.
 * - **Erros viram resultado, não crash**: link sem esquema, nenhuma Activity e
 *   `ActivityNotFoundException` são devolvidos em [Result].
 * - **`FLAG_ACTIVITY_NEW_TASK` automática** quando o `Context` não é uma Activity
 *   (ex.: `applicationContext`); sem ela, `startActivity` lança exceção.
 */
object ResolveDeeplink {

    /** Resultado da resolução: dá para testar e tratar sem callbacks. */
    sealed interface Result {
        /** Encontrou uma Activity; [intent] é explícito e mantém a `Uri` do link. */
        data class Resolved(val intent: Intent) : Result

        /** Nenhuma Activity deste app declara um `<intent-filter>` para o link. */
        data class NotFound(val deeplink: String) : Result

        /** O texto não é um link utilizável (ex.: vazio ou sem esquema). */
        data class Invalid(val deeplink: String) : Result
    }

    /**
     * Descobre qual Activity deste app atende [deeplink], sem abri-la.
     *
     * Se mais de uma Activity atender, usa a primeira da lista do PackageManager
     * (a de maior prioridade no `<intent-filter>`).
     */
    fun resolve(context: Context, deeplink: String): Result {
        val intent = buildViewIntent(context, deeplink) ?: return Result.Invalid(deeplink)
        val activityInfo = context.packageManager
            .queryActivities(intent)
            .firstOrNull()
            ?.activityInfo
            ?: return Result.NotFound(deeplink)

        // Torna o Intent explícito, mantendo action e data (a Uri do link).
        intent.setClassName(activityInfo.packageName, activityInfo.name)
        return Result.Resolved(intent)
    }

    /**
     * Resolve [deeplink] e abre a Activity encontrada.
     *
     * @param activityFlags flags de Activity (ex.: `FLAG_ACTIVITY_CLEAR_TOP`)
     *   aplicadas ao Intent aberto.
     * @return [Result.Resolved] se a Activity foi aberta; caso contrário, o motivo.
     */
    fun open(context: Context, deeplink: String, activityFlags: Int = 0): Result {
        val result = resolve(context, deeplink)
        if (result !is Result.Resolved) return result

        val intent = result.intent.addFlags(activityFlags)
        // Fora de uma Activity não existe uma "task" atual onde empilhar a tela nova.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            result
        } catch (_: ActivityNotFoundException) {
            // Pode acontecer se a Activity foi desabilitada entre a consulta e o start.
            Result.NotFound(deeplink)
        }
    }

    /** Todas as Activities deste app que atendem [deeplink] (vazio se nenhuma). */
    fun queryActivities(context: Context, deeplink: String): List<ResolveInfo> {
        val intent = buildViewIntent(context, deeplink) ?: return emptyList()
        return context.packageManager.queryActivities(intent)
    }

    /** `Intent(ACTION_VIEW, uri)` restrito a este app, ou `null` se o link for inválido. */
    private fun buildViewIntent(context: Context, deeplink: String): Intent? {
        val uri = deeplink.trim().toUri()
        if (uri.scheme.isNullOrBlank()) return null
        return Intent(Intent.ACTION_VIEW, uri).setPackage(context.packageName)
    }

    /**
     * `queryIntentActivities` com a assinatura certa para cada versão do Android.
     *
     * `MATCH_DEFAULT_ONLY` considera só filtros com `category.DEFAULT` — a mesma regra
     * que o `startActivity` aplica a Intents implícitos.
     *
     * Sobre o aviso do lint `QueryPermissionsNeeded` ("Consider adding a <queries>
     * declaration…"): desde o Android 11 (package visibility), o PackageManager
     * esconde os componentes de OUTROS apps, a menos que o manifest declare
     * `<queries>`. O próprio app, porém, é sempre visível para si mesmo. Como o Intent
     * é restrito ao nosso pacote (`setPackage` em [buildViewIntent]), `<queries>` não
     * mudaria o resultado. Declarar um `<queries>` só para calar o aviso daria
     * visibilidade a outros apps sem necessidade (e a Play pede justificativa para
     * visibilidade ampla). Por isso o aviso é suprimido aqui.
     */
    @SuppressLint("QueryPermissionsNeeded")
    private fun PackageManager.queryActivities(intent: Intent): List<ResolveInfo> {
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, flags)
        }
    }
}
