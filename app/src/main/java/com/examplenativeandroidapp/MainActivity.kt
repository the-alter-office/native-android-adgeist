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
 * Screens are fragments swapped inside fragmentContainer.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TITLE_HOME = "Home (Ads)"
        private const val TITLE_SCREEN_TWO = "Screen Two"
        private const val TITLE_SCREEN_THREE = "Screen Three"
    }

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
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
            navigationView.setCheckedItem(R.id.nav_home)
        }
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
            when (item.itemId) {
                R.id.nav_home -> navigateTo({ HomeFragment() }, TITLE_HOME)
                R.id.nav_screen_two -> navigateTo({ PlaceholderFragment.newInstance(TITLE_SCREEN_TWO) }, TITLE_SCREEN_TWO)
                R.id.nav_screen_three -> navigateTo({ PlaceholderFragment.newInstance(TITLE_SCREEN_THREE) }, TITLE_SCREEN_THREE)
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Keep toolbar title and drawer selection in sync while navigating back
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

        // Back closes the drawer if open, otherwise pops the fragment back stack
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
    }

    /**
     * Pushes the selected screen onto the back stack so back retraces the
     * user's history. Reselecting the current screen is a no-op.
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
