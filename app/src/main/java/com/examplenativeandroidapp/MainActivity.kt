package com.examplenativeandroidapp

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.adgeistkit.AdgeistCore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

/**
 * Shell activity: top header with hamburger menu + side navigation drawer.
 *
 * Screens are fragments swapped inside fragmentContainer. Switching from
 * "Home (Ads)" to any other screen destroys HomeFragment's view, detaching the
 * AdView — use this to verify the SDK cleans up the WebView and its listeners.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TITLE_HOME = "Home (Ads)"
        private const val TITLE_SCREEN_TWO = "Screen Two"
        private const val TITLE_SCREEN_THREE = "Screen Three"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdgeistCore with default packageId from build.gradle.kts
        AdgeistCore.initialize(applicationContext)

        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.topToolbar)
        val navigationView: NavigationView = findViewById(R.id.navigationView)

        // On Android 15+ (targetSdk 35) the app draws edge-to-edge, so push the
        // toolbar content below the status bar. Don't consume the insets — the
        // NavigationView uses them to pad its own header.
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        // Same rule for the drawer header: keep its own padding and add the
        // status bar height on top of it.
        val navHeader = navigationView.getHeaderView(0)
        val navHeaderTopPadding = navHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(navHeader) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = navHeaderTopPadding + statusBar.top)
            insets
        }

        // Hamburger icon opens the side nav as an overlay
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> navigateTo({ HomeFragment() }, TITLE_HOME)
                R.id.nav_screen_two -> navigateTo({ PlaceholderFragment.newInstance(TITLE_SCREEN_TWO) }, TITLE_SCREEN_TWO)
                R.id.nav_screen_three -> navigateTo({ PlaceholderFragment.newInstance(TITLE_SCREEN_THREE) }, TITLE_SCREEN_THREE)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Keep the toolbar title and drawer selection in sync as the user
        // navigates back through their history.
        supportFragmentManager.addOnBackStackChangedListener {
            val title = currentScreenTitle()
            toolbar.title = title
            navigationView.setCheckedItem(
                when (title) {
                    TITLE_SCREEN_TWO -> R.id.nav_screen_two
                    TITLE_SCREEN_THREE -> R.id.nav_screen_three
                    else -> R.id.nav_home
                }
            )
        }

        // Back button: close the drawer if open, otherwise let the fragment
        // back stack pop (Screen Two/Three -> Home) before the app exits.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
            navigationView.setCheckedItem(R.id.nav_home)
        }
    }

    /**
     * Pushes the selected screen onto the back stack, so the back button
     * retraces the user's navigation history. Reselecting the screen that is
     * already showing is a no-op.
     */
    private fun navigateTo(createFragment: () -> Fragment, title: String) {
        if (currentScreenTitle() == title) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, createFragment())
            .addToBackStack(title)
            .commit()
        toolbar.title = title
    }

    private fun currentScreenTitle(): String {
        val entryCount = supportFragmentManager.backStackEntryCount
        if (entryCount == 0) return TITLE_HOME
        return supportFragmentManager.getBackStackEntryAt(entryCount - 1).name ?: TITLE_HOME
    }
}
