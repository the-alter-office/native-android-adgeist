package com.examplefragmentapp

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.adgeistkit.AdgeistCore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

/**
 * Shell activity: top header with hamburger menu + side navigation drawer.
 * Screens are fragments swapped inside fragmentContainer.
 */
class MainActivity : AppCompatActivity() {

    private data class Screen(
        val menuId: Int,
        val title: String,
        val create: () -> Fragment
    )

    private val screens = listOf(
        Screen(R.id.nav_home, "Home (Ads)") { HomeFragment() },
        Screen(R.id.nav_fixed, "1. Non-responsive") { FixedAdFragment() },
        Screen(R.id.nav_responsive_both, "2. Responsive - Both Axes") { ResponsiveBothFragment() },
        Screen(R.id.nav_responsive_vertical, "3. Responsive - Vertical") { ResponsiveVerticalFragment() },
        Screen(R.id.nav_responsive_horizontal, "4. Responsive - Horizontal") { ResponsiveHorizontalFragment() },
        Screen(R.id.nav_responsive_scroll, "5. Responsive - ScrollView") { ResponsiveScrollFragment() }
    )

    private val homeScreen get() = screens.first()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AdgeistCore.initialize(applicationContext)

        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.topToolbar)
        navigationView = findViewById(R.id.navigationView)

        applyStatusBarInsets()
        setupNavigation()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, homeScreen.create(), homeScreen.title)
                .commitNow()
        }

        syncChrome()
    }

    /**
     * The app draws edge-to-edge on Android 15+ (targetSdk 35), so the toolbar
     * and drawer header absorb the status bar height as extra top padding.
     */
    private fun applyStatusBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        val navHeader = navigationView.getHeaderView(0)
        val navHeaderTopPadding = navHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(navHeader) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = navHeaderTopPadding + statusBar.top)
            insets
        }
    }

    private fun setupNavigation() {
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            screens.firstOrNull { it.menuId == item.itemId }?.let { navigateTo(it) }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Toolbar title and drawer selection follow whatever is in the container
        supportFragmentManager.addOnBackStackChangedListener { syncChrome() }

        // Back closes the drawer; when it is shut this stays disabled so back
        // reaches the FragmentManager directly
        val closeDrawerOnBack = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        onBackPressedDispatcher.addCallback(this, closeDrawerOnBack)

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                closeDrawerOnBack.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                closeDrawerOnBack.isEnabled = false
            }
        })
    }

    /**
     * Home is the root of the container and is never pushed, so selecting it
     * unwinds the back stack instead of stacking a second copy. Every other
     * screen is pushed so back retraces the user's history.
     */
    private fun navigateTo(screen: Screen) {
        if (currentScreen() == screen) return

        if (screen == homeScreen) {
            popToRoot()
            return
        }

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, screen.create(), screen.title)
            .addToBackStack(screen.title)
            .commit()
    }

    private fun popToRoot() {
        if (supportFragmentManager.backStackEntryCount == 0) return

        supportFragmentManager.popBackStack(
            supportFragmentManager.getBackStackEntryAt(0).id,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

    private fun currentScreen(): Screen {
        val tag = supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.tag
        return screens.firstOrNull { it.title == tag } ?: homeScreen
    }

    private fun syncChrome() {
        val screen = currentScreen()
        toolbar.title = screen.title
        navigationView.setCheckedItem(screen.menuId)
    }
}
