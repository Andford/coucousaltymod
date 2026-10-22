// Tokkip in the car, on Android Auto. Android Auto only lets navigation apps
// draw freely on the screen (the map surface), so Tokkip registers as one and
// paints its own two modes there:
//   · Ambiente — a slow, breathing glow around the screen edge, Tokkip and the time.
//   · Panel    — the time, what's playing and what's left to do today.
// Personal use: installed from the APK with Android Auto's "unknown sources"
// (developer settings). Play would not accept a "navigation" app that doesn't
// navigate, so this has to change before any public release.

package mx.loperstack.mochi.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import mx.loperstack.mochi.R
import mx.loperstack.mochi.data.NowPlaying
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.mochi.Sounds

class TokkipCarService : CarAppService() {
    // Any Android Auto host may connect: fine for a sideloaded personal build.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = TokkipSession()
}

class TokkipSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = TokkipScreen(carContext)
}

class TokkipScreen(context: CarContext) : Screen(context), DefaultLifecycleObserver {
    private val painter = CarPainter(context) { invalidate() }
    private val appManager get() = carContext.getCarService(AppManager::class.java)

    init { lifecycle.addObserver(this) }

    override fun onCreate(owner: LifecycleOwner) {
        Store.init(carContext)
        Store.applyLook()
        Sounds.init(carContext)
        Sounds.enabled = Store.state.value.soundOn
        appManager.setSurfaceCallback(painter)
        painter.onBreak = {
            CarToast.makeText(carContext, "Llevas 2 horas manejando. ¿Una pausa?", CarToast.LENGTH_LONG).show()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        NowPlaying.start(carContext)
        painter.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        painter.stop()
        NowPlaying.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runCatching { appManager.setSurfaceCallback(null) }
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()

    override fun onGetTemplate(): Template {
        val playing = NowPlaying.track.value?.playing == true
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(icon(if (painter.ambient) R.drawable.ic_car_dashboard else R.drawable.ic_car_ambient))
                    .setOnClickListener { painter.toggle(); invalidate() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(if (playing) R.drawable.ic_car_pause else R.drawable.ic_car_play))
                    .setOnClickListener { painter.touch(); NowPlaying.playPause() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_car_next))
                    .setOnClickListener { painter.touch(); NowPlaying.next() }
                    .build(),
            )
            .build()
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }
}
