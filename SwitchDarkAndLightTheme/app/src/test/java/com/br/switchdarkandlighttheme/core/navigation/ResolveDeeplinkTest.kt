package com.br.switchdarkandlighttheme.core.navigation

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen.DarkThemeScreenActivity
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen.StatefulToggleDarkThemeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Testes do [ResolveDeeplink] com Robolectric.
 *
 * O Robolectric carrega o AndroidManifest do app, então os `<intent-filter>` reais
 * (esquema `switchthemeeffect`, hosts `darktheme` e `statetoggle`) são usados na resolução.
 */
@RunWith(RobolectricTestRunner::class)
class ResolveDeeplinkTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun resolve_linkDeclaradoNoManifest_retornaIntentExplicitoParaAActivity() {
        val result = ResolveDeeplink.resolve(application, "switchthemeeffect://darktheme")

        result as ResolveDeeplink.Result.Resolved
        assertEquals(DarkThemeScreenActivity::class.java.name, result.intent.component?.className)
        assertEquals(application.packageName, result.intent.component?.packageName)
    }

    @Test
    fun resolve_mantemAUriDoLinkNoIntent() {
        val deeplink = "switchthemeeffect://statetoggle?dark=true"

        val result = ResolveDeeplink.resolve(application, deeplink)

        result as ResolveDeeplink.Result.Resolved
        assertEquals(StatefulToggleDarkThemeActivity::class.java.name, result.intent.component?.className)
        assertEquals(Intent.ACTION_VIEW, result.intent.action)
        assertEquals(deeplink, result.intent.dataString)
        assertEquals("true", result.intent.data?.getQueryParameter("dark"))
    }

    @Test
    fun resolve_esquemaSemActivity_retornaNotFound() {
        val result = ResolveDeeplink.resolve(application, "naoexiste://home")

        assertEquals(ResolveDeeplink.Result.NotFound("naoexiste://home"), result)
    }

    @Test
    fun resolve_esquemaConhecidoComHostDesconhecido_retornaNotFound() {
        // As duas Activities usam o esquema `switchthemeeffect`; quem decide é o host.
        val result = ResolveDeeplink.resolve(application, "switchthemeeffect://desconhecido")

        assertEquals(ResolveDeeplink.Result.NotFound("switchthemeeffect://desconhecido"), result)
    }

    @Test
    fun resolve_textoSemEsquema_retornaInvalid() {
        listOf("", "   ", "home/settings").forEach { deeplink ->
            assertEquals(
                "deeplink '$deeplink'",
                ResolveDeeplink.Result.Invalid(deeplink),
                ResolveDeeplink.resolve(application, deeplink)
            )
        }
    }

    @Test
    fun queryActivities_retornaApenasAsActivitiesQueAtendemOLink() {
        val activities = ResolveDeeplink.queryActivities(application, "switchthemeeffect://darktheme")

        assertEquals(
            listOf(DarkThemeScreenActivity::class.java.name),
            activities.map { it.activityInfo.name }
        )
        assertTrue(ResolveDeeplink.queryActivities(application, "naoexiste://x").isEmpty())
    }

    @Test
    fun open_aPartirDeUmaActivity_abreODestinoSemNewTask() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        val result = ResolveDeeplink.open(
            activity,
            "switchthemeeffect://darktheme",
            activityFlags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        )

        val started = shadowOf(activity).nextStartedActivity
        assertTrue(result is ResolveDeeplink.Result.Resolved)
        assertEquals(DarkThemeScreenActivity::class.java.name, started.component?.className)
        assertEquals("switchthemeeffect://darktheme", started.dataString)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun open_aPartirDoApplicationContext_adicionaNewTask() {
        ResolveDeeplink.open(application, "switchthemeeffect://statetoggle")

        val started = shadowOf(application).nextStartedActivity
        assertEquals(StatefulToggleDarkThemeActivity::class.java.name, started.component?.className)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun open_linkSemActivity_naoAbreNada() {
        val result = ResolveDeeplink.open(application, "naoexiste://home")

        assertEquals(ResolveDeeplink.Result.NotFound("naoexiste://home"), result)
        assertNull(shadowOf(application).nextStartedActivity)
    }
}
